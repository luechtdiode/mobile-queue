import { Injectable, EventEmitter } from '@angular/core';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';

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
  
  connected = new BehaviorSubject<boolean>(false);
  identified = new BehaviorSubject<boolean>(false);
  ticketCreated = new EventEmitter<TicketMessage>();
  ticketActivated = new EventEmitter<TicketMessage>();
  ticketCalled = new EventEmitter<TicketMessage>();
  ticketAccepted = new EventEmitter<TicketMessage>();
  ticketExpired = new EventEmitter<TicketMessage>();
  ticketDeleted = new EventEmitter<TicketMessage>();
  ticketSummaries = new EventEmitter<UserTicketSummary>();
  
  constructor() { 
    this.init();
  }

  login(username) {
    if (!this.identifiedState) {      
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
    } else if(this.connectedState) {
      this.websocket.send(message);
    }
  }

  disconnectWS() {
    if (this.websocket) {
      this.websocket.close();
    }
  }

  private init() {
    console.log('init');
    const host = location.host;
    const path = location.pathname;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    if (host.startsWith('localhost')) {
      this.backendUrl = "ws://localhost:8080/api/ticketTrigger";
    } else {
      this.backendUrl = protocol +"//" + host + path + "api/ticketTrigger";
    }

    this.getDeviceId();
    this.connect(undefined, true);
  }

  private connect(message?: string, autologin: boolean = false) {
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

    this.websocket.onclose = () => {
      this.websocket = undefined;
      this.identifiedState = false;
      this.identified.next(this.identifiedState);
      this.connectedState = false;
      this.connected.next(this.connectedState);      
    };

    this.websocket.onmessage = (evt) => {
      if (evt.data === 'keepAlive') {
        return;
      }
      if (this.connectedState && evt.data.startsWith('deviceId=')) {
        this.setDeviceId(evt.data.split('=')[1]);
        return;
      }
      try {
        const message = JSON.parse(evt.data);
        const type = message['type'];
        switch(type) {
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
            console.log(message);
        }
      } catch (e) {
        // console.log(e);
      }
    };

    this.websocket.onerror = (e) => {
      this.websocket = undefined;
      this.identifiedState = false;
      this.identified.next(this.identifiedState);
      this.connectedState = false;
      this.connected.next(this.connectedState); 
    };
  };

  public setUsername(n) {
    this.username = n;
    if (typeof (Storage) !== "undefined") {
      localStorage.username = n;
    }
  }

  public getUsername() {
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
