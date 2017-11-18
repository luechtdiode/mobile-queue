import { Component, OnInit } from '@angular/core';
import { NavController, NavParams } from 'ionic-angular';
import { HttpClient } from '@angular/common/http';
import { TicketsService, EventTicketsSummary, User } from '../../app/tickets.service';
import { onDeviceUserUrl, backendUserUrl, onDeviceUrl, backendUrl } from '../../app/utils';
import { EventAdminPage } from '../eventadmin/eventadmin';

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
  usernames = {};

  ionViewDidLoad() {
    console.log('ionViewDidLoad MyeventsPage');
  }

  ngOnInit(): void {
    this.ws.eventTicketSummaries.subscribe(ets => {
      this.myevents = [...this.myevents.filter(e => e.event.id !== ets.event.id), ets].sort((a, b): number => {
        return a.event.eventTitle.localeCompare(b.event.eventTitle);
      });
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
    return summary.invites.filter(item => item.state === 'Issued').reduce((acc, ticket) => acc + ticket.participants, 0);
  }

  createEvent(event: Event) {
    this.http.post(onDeviceUrl, JSON.stringify(event)).subscribe(
      (data: Response) => {
        
      }, (err) => this.http.get(backendUrl).subscribe(
        (data: Response) => {
        
        }));
  }

}
