import { Component, OnInit } from '@angular/core';
import { NavController, NavParams } from 'ionic-angular';
import { HttpClient } from '@angular/common/http';
import { TicketsService, EventTicketsSummary } from '../../app/tickets.service';
import { EventAdminPage } from '../eventadmin/eventadmin';
import { onDeviceUrl, backendUrl } from '../../app/utils';
import { AddEventPage } from '../addevent/addevent';

@Component({
  selector: 'page-myevents',
  templateUrl: 'myevents.html',
})
export class MyeventsPage implements OnInit {

  constructor(public navCtrl: NavController, 
    public navParams: NavParams, public http: HttpClient,
    public ws: TicketsService) {
  }

  myevents: EventTicketsSummary[] = [];

  ionViewDidLoad() {
    console.log('ionViewDidLoad MyeventsPage');
  }

  ngOnInit(): void {
    this.ws.eventTicketSummaries.subscribe(ets => {
      this.myevents = [...this.myevents.filter(e => e.event.id !== ets.event.id), ets].sort((a, b): number => {
        return a.event.eventTitle.localeCompare(b.event.eventTitle);
      });
    });
    this.ws.eventUpdated.subscribe(updatedEvent => {
      this.myevents = this.myevents.map(event => event.event.id !== updatedEvent.id ? event : Object.assign({}, event, {event: updatedEvent}));
    });
    this.ws.eventDeleted.subscribe(eventid => {
      this.myevents = this.myevents.filter(event => event.event.id !== eventid);
    });
  }

  onClick(summary: EventTicketsSummary) {
    this.navCtrl.push(EventAdminPage, { 'eventSummary': summary });
  }


  acceptedInvites(summary: EventTicketsSummary) {
    return summary.invites.filter(item => item.state === 'Confirmed').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  openInvites(summary: EventTicketsSummary) {
    return summary.invites.filter(item => item.state === 'Called').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  waiting(summary: EventTicketsSummary) {
    return summary.invites.filter(item => item.state === 'Issued' || item.state === 'Skipped').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  onCreateNewEvent() {
    this.navCtrl.push(AddEventPage);
  }

  onEditEvent(summary: EventTicketsSummary, slidingItem) {
    slidingItem.close();
    this.navCtrl.push(AddEventPage, {event: summary.event});
  }

  onDeleteEvent(summary: EventTicketsSummary, slidingItem) {
    slidingItem.close();
    this.http.delete(onDeviceUrl + '/' + summary.event.id).subscribe(
      (data: Response) => {
      }, (err) => this.http.delete(backendUrl + '/' + summary.event.id).subscribe(
        (data: Response) => {
        }));
  }  

}
