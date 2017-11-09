import { Injectable, EventEmitter } from '@angular/core';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { formatCurrentMoment } from './utils';

export interface EventSubscription {
  channel: number;
  description: string;
  tickets: number;
}

export interface Ticket {
  state?: string;
  participants?: number;
  userid?: number;
  eventid?: number;
}

export interface UserTicketSummary {
  skippedcnt?: number;
  waitingPosition?: number;
  waitingCnt?: number;
  acceptedCnt?: number;
  calledCnt?: number;
  type?: string;
}

export interface TicketMessage {
  ticket: Ticket;
  type: string;
}

declare var location: any;

@Injectable()
export class TicketsService {
  private deviceId;
  private username;
  private identifiedState = false;
  private connectedState = false;
  private confirmData;
  // private tickets: EventSubscription[] = [];
  private websocket: WebSocket;
  private backendUrl: string;
  private reconnectionObservable: Observable<number>;
  private explicitClosed = false;
  private reconnectInterval: number = 30000; // pause between connections
  private reconnectAttempts: number = 480; // number of connection attempts

  connected = new BehaviorSubject<boolean>(false);
  identified = new BehaviorSubject<boolean>(false);
  ticketCreated = new EventEmitter<TicketMessage>();
  ticketActivated = new EventEmitter<TicketMessage>();
  ticketCalled = new EventEmitter<TicketMessage>();
  ticketAccepted = new EventEmitter<TicketMessage>();
  ticketExpired = new EventEmitter<TicketMessage>();
  ticketDeleted = new EventEmitter<TicketMessage>();
  ticketSummaries = new EventEmitter<UserTicketSummary>();
  logMessages = new EventEmitter<string>();
  lastMessages: string[] = [];

  constructor() {
    this.init();
  }

  get stopped(): boolean {
    return this.explicitClosed;
  }

  login(username) {
    if (!this.identifiedState) {
      this.lastMessages = [];
      this.setUsername(username);
      this.sendMessage(JSON.stringify({
        'type': 'HelloImOnline',
        'username': username,
        'deviceId': this.deviceId
      }));
    }
  }

  subscribeEvent(name, count) {
    if (this.identifiedState) {
      this.sendMessage(JSON.stringify({
        'type': 'Subscribe',
        'channel': parseInt(name),
        'count': parseInt(count)
      }));
    }
  }

  confirm() {
    if (this.confirmData && this.identifiedState) {
      this.sendMessage(this.confirmData.replace('TicketCalled', 'TicketConfirmed'));
      this.confirmData = undefined;
    }
  }

  skip() {
    if (this.confirmData && this.identifiedState) {
      this.sendMessage(this.confirmData.replace('TicketCalled', 'TicketSkipped'));
      this.confirmData = undefined;
    }

  }

  unsubscribeEvent(name) {
    if (this.identifiedState) {
      this.sendMessage(JSON.stringify({
        'type': 'UnSubscribe',
        'channel': parseInt(name)
      }));
    }
  }

  sendMessage(message) {
    if (!this.websocket) {
      this.connect(message);
    } else if (this.connectedState) {
      this.websocket.send(message);
    }
  }

  disconnectWS() {
    this.explicitClosed = true;
    if (this.websocket) {
      this.websocket.close();
    } else {
      this.close();
    }
  }

  private close() {
    this.websocket = undefined;
    this.identifiedState = false;
    this.identified.next(this.identifiedState);
    this.connectedState = false;
    this.connected.next(this.connectedState);
  }

  private isWebsocketConnected(): boolean {
    return this.websocket && this.websocket.readyState === this.websocket.OPEN;
  }
  private isWebsocketConnecting(): boolean {
    return this.websocket && this.websocket.readyState === this.websocket.CONNECTING;
  }
  private shouldConnectAgain(): boolean {
    return !(this.isWebsocketConnected() || this.isWebsocketConnecting());
  }

  /// reconnection
  reconnect(): void {
    if (!this.reconnectionObservable) {
      this.logMessages.next('start try reconnection ...');
      this.reconnectionObservable = Observable.interval(this.reconnectInterval)
        .takeWhile((v, index) => {
          // this.websocket = undefined;
          return index < this.reconnectAttempts && !this.explicitClosed
        });
      const subsr = this.reconnectionObservable.subscribe(
        () => {
          if (this.shouldConnectAgain()) {
            this.logMessages.next('continue with reconnection ...');
            this.connect(undefined, true);
          }
        },
        null,
        () => {
          /// if the reconnection attempts are failed, then we call complete of our Subject and status
          this.reconnectionObservable = null;
          subsr.unsubscribe();
          if (this.isWebsocketConnected()) {
            this.logMessages.next('finish with reconnection (successfull)');
          } else if (this.isWebsocketConnecting()) {
            this.logMessages.next('continue with reconnection (CONNECTING)');
          } else if (!this.websocket || this.websocket.CLOSING || this.websocket.CLOSED) {
            this.disconnectWS();
            this.logMessages.next('finish with reconnection (unsuccessfull)');
          }
        });
    }
  }

  private init() {
    this.logMessages.subscribe(msg => {
      this.lastMessages.push(formatCurrentMoment(true) + ` - ${msg}`);
      this.lastMessages = this.lastMessages.slice(Math.max(this.lastMessages.length - 50, 0));
    });
    this.logMessages.next('init');
    const host = location.host;
    const path = location.pathname;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    if (!host || host === '') {
      this.backendUrl = "wss://38qniweusmuwjkbr.myfritz.net/mbq/api/ticketTrigger";
    } else if (host.startsWith('localhost')) {
        this.backendUrl = "ws://localhost:8080/api/ticketTrigger";
      } else {
      this.backendUrl = protocol + "//" + host + path + "api/ticketTrigger";
    }
    this.logMessages.next('init with ' + this.backendUrl);

    this.getDeviceId();
    this.connect(undefined, (!!this.getUsername() && !!this.getDeviceId()));
  }

  private connect(message?: string, autologin: boolean = false) {
    this.explicitClosed = false;
    this.websocket = new WebSocket(this.backendUrl);
    this.websocket.onopen = () => {
      this.connectedState = true;
      this.connected.next(this.connectedState);
      var un = this.getUsername();
      if (autologin && this.deviceId && this.deviceId !== '' && !this.identifiedState && un && un !== '') {
        this.login(this.getUsername());
      }
      if (message) {
        this.sendMessage(message);
      }
    };

    this.websocket.onclose = (evt: CloseEvent) => {
      this.close();
      switch (evt.code) {
        case 1001: this.logMessages.next('Going Away');
          break;
        case 1002: this.logMessages.next('Protocol error');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1003: this.logMessages.next('Unsupported Data');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1005: this.logMessages.next('No Status Rcvd');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1006: this.logMessages.next('Abnormal Closure');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1007: this.logMessages.next('Invalid frame payload data');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1008: this.logMessages.next('Policy Violation');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1009: this.logMessages.next('Message Too Big');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1010: this.logMessages.next('Mandatory Ext.');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1011: this.logMessages.next('Internal Server Error');
          if (!this.explicitClosed) {
            /// in case of an error with a loss of connection, we restore it
            this.reconnect();
          }
          break;
        case 1015: this.logMessages.next('TLS handshake');
          break;
        default:
      }
    };

    const sendMessageAck = (evt: MessageEvent) => {
      this.sendMessage(JSON.stringify({
        'type': 'MessageAck',
        'msg': evt.data
      }));
    };

    this.websocket.onmessage = (evt: MessageEvent) => {
      if (evt.data.startsWith('Please authenticate')) {
        return;
      }
      if (evt.data === 'keepAlive') {
        sendMessageAck(evt);
        return;
      }
      if (this.connectedState && evt.data.startsWith('deviceId=')) {
        this.setDeviceId(evt.data.split('=')[1]);
        sendMessageAck(evt);
        return;
      }
      try {
        const message = JSON.parse(evt.data);
        const type = message['type'];
        switch (type) {
          case 'TicketIssued':
            this.ticketCreated.next(message);
            break;
          case 'TicketReactivated':
            this.ticketActivated.next(message);
            break;
          case 'TicketCalled':
            this.confirmData = evt.data;
            this.ticketCalled.next(this.confirmData);
            break;
          case 'TicketAccepted':
            this.ticketAccepted.next(message);
            break;
          case 'TicketExpired':
            this.ticketExpired.next(message);
            break;
          case 'TicketDeleted':
            this.ticketDeleted.next(message);
          case 'UserTicketsSummary':
            this.ticketSummaries.next(message);
            break;
          default:
            this.logMessages.next(message);
        }
      } catch (e) {
        this.logMessages.next(e + ": " + evt.data);
      }
    };

    this.websocket.onerror = (e: ErrorEvent) => {
      this.close();
    };
  };

  public setUsername(n: string) {
    this.username = n;
    if (typeof (Storage) !== "undefined") {
      localStorage.username = n;
    }
  }

  public getUsername(): string {
    if (typeof (Storage) !== "undefined") {
      this.username = localStorage.username;
    }
    if (!this.username) {
      this.username = '';
    }
    return this.username;
  }

  private setDeviceId(id: string) {
    if (id) {
      this.identifiedState = true;
      this.identified.next(this.identifiedState);
      if (!this.deviceId || this.deviceId === '' || this.deviceId === 'undefined') {
        this.deviceId = id;
      }
      if (typeof (Storage) !== "undefined") {
        localStorage.deviceId = id;
      }
    }
  }

  private getDeviceId(): string {
    if (typeof (Storage) !== "undefined") {
      this.deviceId = localStorage.deviceId;
    }
    if (!this.deviceId) {
      this.deviceId = '';
    }
    return this.deviceId;
  }
}
