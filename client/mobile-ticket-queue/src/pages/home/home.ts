import { Component, OnInit, OnDestroy } from '@angular/core';

import { HttpClient } from '@angular/common/http';
import { NavController } from 'ionic-angular';
import { AlertController, Platform } from 'ionic-angular';

import { TicketsService, EventSubscription, Ticket, Event, EventResponse, TicketMessage } from '../../app/tickets.service';
import { Observable } from 'rxjs/Observable';
import { SettingsPage } from '../settings/settings';
import { SubscribePage } from '../subscribe/subscribe';
import { Subscription } from 'rxjs';
import { BackgroundMode } from '@ionic-native/background-mode';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { combineLatest } from 'rxjs/operator/combineLatest';

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage implements OnInit, OnDestroy {
  externalLoaderSubscription: Subscription;
  activatedSubscription: Subscription;
  createdSubscription: Subscription;

  searchQuery: string = '';
  connected = false;
  items: Event[] = [];
  filtereditems: Event[] = [];
  unsubscribedItems: Event[] = [];
  ticketSubscriptions: EventSubscription[] = [];

  constructor(public navCtrl: NavController, public alertCtrl: AlertController, public ws: TicketsService, public http: HttpClient,
    private backgroundMode: BackgroundMode, public platform: Platform) {
  }

  ngOnInit(): void {
    this.ws.disconnectWS();
    this.initializeItems();    
    
    if (!this.ws.getUsername) {
      this.navCtrl.push(SettingsPage);              
    }
    this.ws.connected.subscribe(c => {
      this.connected = c;
      if (this.connected) {
        this.initializeItems();
        this.ticketSubscriptions = [];
      }
    });
    this.externalLoaderSubscription = Observable.combineLatest(this.ws.identified,  Observable.interval(1000)).subscribe(latest => {
      const [identified, tick] = latest;
      const url = localStorage.getItem("external_load");
      
      if (this.navCtrl.getActive().component === HomePage && identified && url && url.startsWith('mobileticket://') && this.items.length > 0) {
        localStorage.removeItem("external_load");
        this.ws.logMessages.next(url);
        const [protocol, entity, id, command] = url.split('/').filter(s => s.length > 0);
        this.ws.logMessages.next('External loadrequest: entity=' + entity + ", id=" + id + ", command=" + command);
        switch(command) {
          case 'subscribe':
          default:
            this.items.filter(i => i.id === parseInt(id)).forEach(event => this.subscribe(event));
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.activatedSubscription) {
      this.activatedSubscription.unsubscribe();
      this.createdSubscription.unsubscribe();
      this.externalLoaderSubscription.unsubscribe();
    }
    console.log('home destroyed');
  }

  initializeItems() {
    const host = location.host;
    const path = location.pathname;
    const protocol = location.protocol;
    const backendUrl = protocol + "//" + host + path + "api/events";
    const onDeviceUrl = "https://38qniweusmuwjkbr.myfritz.net/mbq/api/events";
    this.http.get(onDeviceUrl).subscribe(
      (data: EventResponse) => {
        this.setItmes(data);
      }, (err) => this.http.get(backendUrl).subscribe(
        (data: EventResponse) => {
          this.setItmes(data);
        }));
  }

  setItmes(response: EventResponse) {
    this.items = response.events;

    this.updateUnsubscribedItems();
    console.log('items retrieved');

    const addTicket = (msg: TicketMessage) => {
      this.removeTicket(msg.ticket);
      this.ticketSubscriptions.push(<EventSubscription>{
        description: this.getEventText(msg.ticket.eventid),
        ticket: msg.ticket
      });
      this.updateUnsubscribedItems();
    };

    if (!this.createdSubscription) {
      this.createdSubscription = this.ws.ticketCreated.subscribe(msg => {
        console.log('ticket issued');
        addTicket(msg);
      });

      this.activatedSubscription = this.ws.ticketActivated.subscribe(msg => {
        console.log('ticket activated');
        addTicket(msg);
      });
      this.ws.init();
    }
  }

  updateUnsubscribedItems() {
    this.unsubscribedItems = this.items
      .filter((item) => {
        if (this.searchQuery && this.searchQuery.trim() !== '') {
          return (item.eventTitle.toLowerCase().indexOf(this.searchQuery.toLowerCase()) > -1);
        } else {
          return true;
        }
      })
      .filter((item) => {
        return this.ticketSubscriptions.filter(subscr => subscr.ticket.eventid === item.id).length === 0;
      });
    if (this.platform.is('cordova')) {
      try {
        if (this.ticketSubscriptions.length > 0) {
          this.backgroundMode.setDefaults({
            title: 'Mobile Ticket Queue',
            text: 'is listening on ticket-service'
          });
          this.backgroundMode.enable();
        } else {
          this.backgroundMode.disable();
        }
      } catch (e) {

      }
    }
  }

  getEvents(ev: any) {
    this.searchQuery = ev.target.value;
    // Reset items back to all of the items
    this.initializeItems();
  }

  getEventText(eventId: number) {
    return this.items.filter(i => i.id == eventId).map(i => i.eventTitle).join('');
  }

  get username() {
    return this.ws.getUsername();
  }
  set username(name: string) {
    this.ws.setUsername(name);
  }

  connectedState(): Observable<boolean> {
    return this.ws.connected;
  }

  loggedIn(): Observable<boolean> {
    return this.ws.identified;
  }

  loggedInText(): Observable<any> {
    return this.ws.identified.map(c => c ? `User ${this.ws.getUsername()} Connected` : `User ${this.ws.getUsername()} Disconnected`);
  }

  settings() {
    this.navCtrl.push(SettingsPage);
  }

  subscribe(event: Event) {
    if (this.connected) {
      this.navCtrl.push(SubscribePage, { event: event });      
    } else {
      let alert = this.alertCtrl.create({
        title: 'Disconnected',
        subTitle: 'Please apply all your Settings to connect with the service',
        buttons: ['OK']
      });
      alert.present();
    }
  }

  private removeTicket(ticket: Ticket) {
    this.ticketSubscriptions = this.ticketSubscriptions.filter(subscr => subscr.ticket.eventid !== ticket.eventid);    
  }

  onTicketClosed(ticket: Ticket) {
    this.removeTicket(ticket);
    this.initializeItems();
  }
}