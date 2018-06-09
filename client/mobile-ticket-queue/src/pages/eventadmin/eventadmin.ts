import { Component, Input, OnDestroy, OnInit } from '@angular/core';
// import { BarcodeScanner } from '@ionic-native/barcode-scanner';
import { TicketsService, EventTicketsSummary } from '../../app/tickets.service';
import { secureHostURL, onDeviceUrl, backendUrl } from '../../app/utils';
import { Subscription } from 'rxjs';
import { Observable } from 'rxjs/Observable';
import { NavParams } from 'ionic-angular';
import { HttpClient } from '@angular/common/http';
import { UsersService } from '../../app/users.service';

@Component({
  selector: 'page-eventadmin',
  templateUrl: 'eventadmin.html'
})
export class EventAdminPage implements OnInit, OnDestroy {
  tsUpdatedSubscription: Subscription;
  mobileUrl: string;
  browserUrl: string;
  tsSubscription: Subscription;
  
  private _eventSummary: EventTicketsSummary;

  constructor(private http: HttpClient, private users: UsersService, public navParams: NavParams, 
    public ws: TicketsService) {
    this.eventSummary = navParams.get('eventSummary');
  }
  
  ngOnDestroy(): void {
    this.tsSubscription.unsubscribe();
    this.tsUpdatedSubscription.unsubscribe();
  }

  ngOnInit(): void {
    this.tsSubscription = this.ws.eventTicketSummaries.subscribe(ets => {
      if (ets.event.id === this.eventSummary.event.id) {
        this.eventSummary = ets;
      }
    });
    this.tsUpdatedSubscription = this.ws.eventUpdated.subscribe(updatedEvent => {
      this.eventSummary.event = this.eventSummary.event.id !== updatedEvent.id ?  this.eventSummary.event : updatedEvent;
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
    this.mobileUrl = 'mobileticket://events/' + summary.event.id + '/subscribe';
    this.browserUrl = secureHostURL + '?subscribe=' + summary.event.id;
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
    return this._eventSummary.invites.filter(item => item.state === 'Issued' || item.state === 'Skipped').reduce((acc, ticket) => acc + ticket.participants, 0);
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
