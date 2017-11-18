import { Component, Input, Output, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { TicketsService, EventSubscription, UserTicketSummary, TicketMessage, Ticket, EventTicketsSummary } from '../../app/tickets.service';
import { formatCurrentMoment, onDeviceUrl, backendUrl } from '../../app/utils';
import { Vibration } from '@ionic-native/vibration';
import { Subscription } from 'rxjs';
import { Observable } from 'rxjs/Observable';
import { BackgroundMode } from '@ionic-native/background-mode';
import { LocalNotifications } from '@ionic-native/local-notifications';
import { Toast } from '@ionic-native/toast';
import { SubscribePage } from '../../pages/subscribe/subscribe';
import { Platform, NavParams } from 'ionic-angular';
import { TranslateService } from 'ng2-translate';
import { HttpClient } from '@angular/common/http';
import { UsersService } from '../../app/users.service';
import { subscribeOn } from 'rxjs/operator/subscribeOn';

@Component({
  selector: 'page-eventadmin',
  templateUrl: 'eventadmin.html'
})
export class EventAdminPage implements OnInit, OnDestroy {
  
  private _eventSummary: EventTicketsSummary;

  constructor(private http: HttpClient, private users: UsersService, public navParams: NavParams, public ws: TicketsService) {
    this.eventSummary = navParams.get('eventSummary');
  }
  
  ngOnDestroy(): void {
    
  }
  ngOnInit(): void {
    this.ws.eventTicketSummaries.subscribe(ets => {
      if (ets.event.id === this.eventSummary.event.id) {
        this.eventSummary = ets;
      }
    });
  }

  @Input()
  set eventSummary(summary: EventTicketsSummary) {
    this._eventSummary = summary;
    this._eventSummary.invites = this._eventSummary.invites
      .filter(item => item.state !== 'Closed')
      .sort((a, b) => {
        if (a.id < b.id) return -1;
        if (a.id > b.id) return 1;
        return 0;
      });
    this.maxInvites = summary.event.groupsize * 2
  }

  get eventSummary(): EventTicketsSummary {
    return this._eventSummary;
  }

  maxInvites = 10;

  username(userid: number): Observable<string> {
    return this.users.getUserName(userid);
  }

  acceptedInvites() {
    return this._eventSummary.invites.filter(item => item.state === 'Confirmed').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  openInvites() {
    return this._eventSummary.invites.filter(item => item.state === 'Called').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  waiting() {
    return this._eventSummary.invites.filter(item => item.state === 'Issued').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  progress() {
    const accepted = this.acceptedInvites();
    if (!accepted) {
      return 0;
    }
    return 100 * accepted / (accepted + this.openInvites());
  }

  invite(event: EventTicketsSummary) {
    console.log(event);
    this.http.get(onDeviceUrl + `/${event.event.id}/${event.event.groupsize}`).subscribe(
      (data: Response) => {
        
      }, (err) => this.http.get(backendUrl + `/${event.event.id}/${event.event.groupsize}`).subscribe(
        (data: Response) => {
        
        }));
  }
}
