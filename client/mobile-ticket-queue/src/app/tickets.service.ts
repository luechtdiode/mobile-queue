import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { formatCurrentMoment } from './utils';
import { ReplaySubject } from 'rxjs';


export interface Event {
  id: number;
  date: Date;
  eventTitle: string;
  userid: number;
  groupsize: number;
}

export interface EventResponse {
  events: Event[];
}

export interface Ticket {
  id?: number;
  state?: string;
  participants?: number;
  userid?: number;
  eventid?: number;
}
export interface EventSubscription {
  description?: string;
  ticket?: Ticket;
}

export interface UserTicketSummary {
  eventid?: number;
  skippedcnt?: number;
  waitingPosition?: number;
  waitingCnt?: number;
  acceptedCnt?: number;
  calledCnt?: number;
  type?: string;
}

export interface EventTicketsSummary {
  event: Event;
  invites: Ticket[];
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
  private websocket: WebSocket;
  private backendUrl: string;
  private reconnectionObservable: Observable<number>;
  private explicitClosed = true;
  private reconnectInterval: number = 30000; // pause between connections
  private reconnectAttempts: number = 480; // number of connection attempts
  private lstKeepAliveReceived: number = 0;
  private keepAliveObserverTimerToken;

  connected = new BehaviorSubject<boolean>(false);
  identified = new BehaviorSubject<boolean>(false);
  ticketCreated = new ReplaySubject<TicketMessage>(10);
  ticketActivated = new ReplaySubject<TicketMessage>(10);
  ticketCalled = new ReplaySubject<TicketMessage>(10);
  ticketAccepted = new ReplaySubject<TicketMessage>(10);
  ticketSkipped = new ReplaySubject<TicketMessage>(10);
  ticketExpired = new ReplaySubject<TicketMessage>(10);
  ticketDeleted = new ReplaySubject<TicketMessage>(10);
  ticketSummaries = new ReplaySubject<UserTicketSummary>();
  eventTicketSummaries = new ReplaySubject<EventTicketsSummary>();
  logMessages = new BehaviorSubject<string>("");
  lastMessages: string[] = [];

  constructor() {
  }

  get stopped(): boolean {
    return this.explicitClosed;
  }

  startKeepAliveObservation() {
    this.keepAliveObserverTimerToken = setTimeout(() => {
      const yet = new Date().getTime();
      const lastSeenSince = yet - this.lstKeepAliveReceived;
      if (!this.explicitClosed
         && !this.reconnectionObservable 
         && lastSeenSince > this.reconnectInterval) {
          this.logMessages.next('connection verified since ' + lastSeenSince + 'ms. It seems to be dead and need to be reconnected!');
          this.disconnectWS(false);
          this.reconnect();
      } else {
        this.logMessages.next('connection verified since ' + lastSeenSince + 'ms');
      }
      this.startKeepAliveObservation();
    }, this.reconnectInterval);    
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

  confirm(ticketCalled: Ticket) {
    this.sendMessage(JSON.stringify({
      'type': 'TicketConfirmed',
      'ticket': ticketCalled
    }));
  }

  skip(ticketCalled: Ticket) {
    this.sendMessage(JSON.stringify({
      'type': 'TicketSkipped',
      'ticket': ticketCalled
    }));
  }

  unsubscribeEvent(eventId) {
    if (this.identifiedState) {
      this.sendMessage(JSON.stringify({
        'type': 'UnSubscribe',
        'channel': parseInt(eventId)
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

  disconnectWS(explicit = true) {
    this.explicitClosed = explicit;
    if (this.websocket) {
      this.websocket.close();
      if (explicit) {
        this.close();  
      }
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

  public init() {
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
    this.startKeepAliveObservation();    
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
      // for(let idx = 0; idx < localStorage.length; idx++) {
      //   this.logMessages.next('LocalStore Item ' + localStorage.key(idx) + " = " + localStorage.getItem(localStorage.key(idx)));
      // }
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
      this.lstKeepAliveReceived = new Date().getTime();
      if (evt.data.startsWith('Connection established.')) {
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
            this.ticketCalled.next(message);
            break;
          case 'TicketAccepted':
            this.ticketAccepted.next(message);
            break;
          case 'TicketSkipped':
            this.ticketSkipped.next(message);
            break;
          case 'TicketExpired':
            this.ticketExpired.next(message);
            break;
          case 'TicketClosed':
            this.ticketDeleted.next(message);
          case 'UserTicketsSummary':
            this.ticketSummaries.next(message);
            break;
          case 'EventTicketsSummary':
          this.eventTicketSummaries.next(message);
          default:
            this.logMessages.next('unknown message: ' + evt.data);
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
