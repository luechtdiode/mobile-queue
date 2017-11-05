import {
  Component
}
  from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavController } from 'ionic-angular';
import { AlertController } from 'ionic-angular';

import { TicketsService, UserTicketSummary } from '../../app/tickets.service';
import { Observable } from 'rxjs/Observable';

interface Event {
  id: number;
  date: Date;
  eventTitle: string;
  userid: number;
}
interface EventResponse {
  events: Event[];
}

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage {
 
  subscribedEvent;
  subscribedTicket;

  eventModel;
  countModel = 1;

  searchQuery: string = '';
  items: Event[] = [];

  lastMessageTitle: string = '';
  lastMessage: string = '';
  ticketSummary: UserTicketSummary;

  constructor(public navCtrl: NavController, public alertCtrl: AlertController, public ws: TicketsService, public http: HttpClient) {
    ws.ticketCreated.subscribe(msg => {
      if (!this.subscribedEvent) {
        console.log(msg);
        this.subscribedEvent = msg.ticket.eventid;
        this.subscribedTicket = msg.ticket;        
        this.countModel = msg.ticket.count;
      }
      this.lastMessageTitle = `${this.formatCurrentMoment()} - Ticket registered`;
      this.lastMessage = `You'll be called 10 minutes before Your Event "${this.getEventText()}" starts!`;
      let alert = this.alertCtrl.create({
        title: this.lastMessageTitle,
        subTitle: this.lastMessage,
        buttons: ['OK']
      });
      alert.present();
    });
    ws.ticketActivated.subscribe(msg => {
      console.log(msg);
      if (!this.subscribedEvent) {
        this.subscribedEvent = msg.ticket.eventid;
        this.subscribedTicket = msg.ticket;
      }
      this.lastMessageTitle = `${this.formatCurrentMoment()} - Ticket (re-)activated`;
      this.lastMessage = `You will be called 10 minutes before Your Event "${this.getEventText()}" starts!`;
      let alert = this.alertCtrl.create({
        title: this.lastMessageTitle,
        subTitle: this.lastMessage,
        buttons: ['OK']
      });
      alert.present();
    });
    ws.ticketCalled.subscribe(msg =>{
      this.lastMessageTitle = `${this.formatCurrentMoment()} - Let's go`;
      this.lastMessage = "Please confirm. Will you be ready in 10 minutes?";
      let confirm = this.alertCtrl.create({
        title: this.lastMessageTitle,
        message: this.lastMessage,
        buttons: [
          {
            text: 'Skip to next iteration',
            handler: data => {
              ws.skip();
              this.lastMessageTitle = `${this.formatCurrentMoment()} - I'm Not ready yet`;
              this.lastMessage = "I skipped my invitation to the next iteration";
            }
          },
          {
            text: 'Confirm',
            handler: data => {
              this.lastMessageTitle = `${this.formatCurrentMoment()} - Confirmed`;
              this.lastMessage = "Yes, i'll be there";
              ws.confirm();
            }
          }
        ]
      });
      confirm.present();
    });
    ws.ticketAccepted.subscribe(msg => {
      this.subscribedEvent = undefined;
      this.lastMessageTitle = `${this.formatCurrentMoment()} - Ticket accepted`;
      this.lastMessage = `We expect you in 10 minutes at ${this.getEventText()}!`;
      let alert = this.alertCtrl.create({
        title: this.lastMessageTitle,
        subTitle: this.lastMessage,
        buttons: ['OK']
      });
      alert.present();
    });
    ws.ticketSummaries.subscribe((summary: UserTicketSummary) => {
      this.ticketSummary = summary;
    });
    this.initializeItems();
  }

  formatCurrentMoment() {
    const d = new Date();
    const datestring = ("0" + d.getDate()).slice(-2) + "-" + ("0"+(d.getMonth()+1)).slice(-2) + "-" +
    d.getFullYear() + " " + ("0" + d.getHours()).slice(-2) + ":" + ("0" + d.getMinutes()).slice(-2);
    return datestring;
  }

  initializeItems() {
    const host = location.host;
    const path = location.pathname;
    const protocol = location.protocol;
    const backendUrl  = protocol +"//" + host + path + "api/events";
    
    this.http.get(backendUrl).subscribe((data: EventResponse) => {     
      this.items = data.events;
      this.eventModel = this.items.length > 0 ? this.items[0].id : undefined;
    });
  }

  getEventText() {
    return this.items.filter(i => i.id == parseInt(this.eventModel)).map(i => i.eventTitle)[0];
  }
  getItems(ev: any) {
    // Reset items back to all of the items
    this.initializeItems();

    // set val to the value of the searchbar
    let val = ev.target.value;

    // if the value is an empty string don't filter the items
    if (val && val.trim() != '') {
      this.items = this.items.filter((item) => {
        return (item.eventTitle.toLowerCase().indexOf(val.toLowerCase()) > -1);
      })
    }
  }

  get username() {
    return this.ws.getUsername();
  }
  set username(name: string) {
    this.ws.setUsername(name);
  }
  
  loggedIn(): Observable<boolean> {
    return this.ws.connected;
  }

  loggedInText(): Observable<any> {
    return this.ws.connected.do((flag: boolean) => {
      if (flag) {

      } else {
        this.lastMessageTitle = '';
        this.lastMessage = '';
        this.ticketSummary = undefined;
      }
    }).map(c => c ? `User ${this.ws.getUsername()} Connected` : `User ${this.ws.getUsername()} Disconnected`);
  }

  logIn(name) {
    this.ws.login(name);
    this.initializeItems();
  }

  logOut() {
    this.ws.disconnectWS();
  }

  registerTicketForEvent(eventId, count) {
    this.ws.subscribeEvent(eventId, count);
    this.subscribedEvent = eventId;
  }
  
  unregisterTicketForEvent() {
    this.ws.unsubscribeEvent(this.subscribedEvent);
    this.subscribedEvent = undefined;
    this.lastMessageTitle = `${this.formatCurrentMoment()} - Ticket returned`;
    this.lastMessage = `You're no longer waiting for ${this.getEventText()}!`;
  }

}