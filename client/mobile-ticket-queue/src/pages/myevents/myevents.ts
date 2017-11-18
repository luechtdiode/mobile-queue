import { Component, OnInit } from '@angular/core';
import { IonicPage, NavController, NavParams } from 'ionic-angular';
import { HttpClient } from '@angular/common/http';
import { TicketsService, EventTicketsSummary, User } from '../../app/tickets.service';

const host = location.host;
const path = location.pathname;
const protocol = location.protocol;
const backendUrl = protocol + "//" + host + path + "api/events";
const onDeviceUrl = "https://38qniweusmuwjkbr.myfritz.net/mbq/api/events";
const backendUserUrl = protocol + "//" + host + path + "api/users";
const onDeviceUserUrl = "https://38qniweusmuwjkbr.myfritz.net/mbq/api/users";

@IonicPage()
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
      ets.invites.filter(invite => this.usernames[invite.userid] === undefined)
      .forEach(invite => {
        this.http.get(onDeviceUserUrl + '/' + invite.userid).subscribe(
          (data: User) => {
            this.usernames[invite.userid] = data.name;
          }, (err) => this.http.get(backendUserUrl + '/' + invite.userid).subscribe(
            (data: User) => {
              this.usernames[invite.userid] = data.name;
              // data.json().then(user => this.usernames[invite.userid] = user.name);
            }))});
      this.myevents = [...this.myevents.filter(e => e.event.id !== ets.event.id), ets].sort((a, b): number => {
        return a.event.eventTitle.localeCompare(b.event.eventTitle);
      });
    });
  }

  getUserName(id: number) {
    return this.usernames[id] || id;
  }

  createEvent(event: Event) {
    this.http.post(onDeviceUrl, JSON.stringify(event)).subscribe(
      (data: Response) => {
        
      }, (err) => this.http.get(backendUrl).subscribe(
        (data: Response) => {
        
        }));
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
