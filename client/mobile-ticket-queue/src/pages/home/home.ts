import { Component, OnInit, OnDestroy } from '@angular/core';

import { HttpClient } from '@angular/common/http';
import { NavController } from 'ionic-angular';
import { AlertController, Platform } from 'ionic-angular';

import { TicketsService, EventSubscription, Ticket, Event, EventResponse, TicketMessage } from '../../app/tickets.service';
import { SettingsPage } from '../settings/settings';
import { SubscribePage } from '../subscribe/subscribe';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs';
import { BackgroundMode } from '@ionic-native/background-mode';
import { TranslateService } from '@ngx-translate/core';
import { onDeviceUrl, backendUrl } from '../../app/utils';

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage implements OnInit, OnDestroy {
  externalLoaderSubscription: Subscription;
  activatedSubscription: Subscription;
  createdSubscription: Subscription;
  showActions: false;
  searchQuery: string = '';
  connected = false;
  items: Event[] = [];
  filtereditems: Event[] = [];
  unsubscribedItems: Event[] = [];
  ticketSubscriptions: EventSubscription[] = [];

  constructor(public navCtrl: NavController, public alertCtrl: AlertController, public ws: TicketsService, public http: HttpClient,
    private backgroundMode: BackgroundMode, public platform: Platform,
    private translate: TranslateService) {
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
    this.ws.eventUpdated.subscribe(updatedEvent => {
      this.items = this.items.map(event => event.id !== updatedEvent.id ? event : updatedEvent);
      this.filtereditems = this.filtereditems.map(event => event.id !== updatedEvent.id ? event : updatedEvent);
      this.unsubscribedItems = this.unsubscribedItems.map(event => event.id !== updatedEvent.id ? event : updatedEvent);
    });
    this.ws.eventDeleted.subscribe(eventid => {
      this.items = this.items.filter(event => event.id !== eventid);
      this.filtereditems = this.filtereditems.filter(event => event.id !== eventid);
      this.unsubscribedItems = this.unsubscribedItems.filter(event => event.id !==eventid);
      this.ticketSubscriptions = this.ticketSubscriptions.filter(sub => sub.ticket.eventid !== eventid);
    });
    this.externalLoaderSubscription = Observable.combineLatest(this.ws.identified,  Observable.interval(1000)).subscribe(latest => {
      const [identified, ] = latest;
      const url = localStorage.getItem("external_load");
      
      if (this.navCtrl.getActive().component === HomePage && identified && url && url.startsWith('mobileticket://') && this.items.length > 0) {
        localStorage.removeItem("external_load");
        this.ws.logMessages.next(url);
        const [, entity, id, command] = url.split('/').filter(s => s.length > 0);
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
  }

  initializeItems() {
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
        addTicket(msg);
      });

      this.activatedSubscription = this.ws.ticketActivated.subscribe(msg => {
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
            text: this.translate.instant('messages.is listening on ticket-service')
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
    return this.ws.identified.map(c => c ? this.translate.instant("messages.UserConnected", {"username": this.ws.getUsername()}) : this.translate.instant("messages.UserDisconnected", {"username": this.ws.getUsername()}));
  }

  settings() {
    this.navCtrl.push(SettingsPage);
  }

  subscribe(event: Event) {
    if (this.connected) {
      this.navCtrl.push(SubscribePage, { event: event });      
    } else {
      let alert = this.alertCtrl.create({
        title: this.translate.instant("texts.Disconnected"),
        subTitle: this.translate.instant("messages.ApplySettings"),
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