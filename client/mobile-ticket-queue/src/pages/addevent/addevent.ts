import { Component } from '@angular/core';
import { NavController, NavParams } from 'ionic-angular';
import { TicketsService, Event } from '../../app/tickets.service';
import { HttpClient } from '@angular/common/http';
import { onDeviceUrl, backendUrl } from '../../app/utils';
import { HttpHeaders } from '@angular/common/http';

const headers = new HttpHeaders().set('Content-Type', 'application/json; charset=utf-8');

@Component({
  selector: 'page-addevent',
  templateUrl: 'addevent.html',
})
export class AddEventPage {

  event: Event;

  constructor(public navCtrl: NavController, public navParams: NavParams, private http: HttpClient, public ws: TicketsService) {
    this.event = navParams.get('event');
    if(!this.event) {
      ws.authenticatedUser
      this.event = <Event> {
        id: 0,
        userid: ws.authenticatedUser.id,
        eventTitle: '',
        groupsize: 10,
        date: undefined
      }
    }
  }

  createEvent() {
    this.http.post(onDeviceUrl, JSON.stringify(this.event), {headers: headers}).subscribe(
      (data: Response) => {
        this.navCtrl.pop();        
      }, (err) => this.http.post(backendUrl, JSON.stringify(this.event), {headers: headers}).subscribe(
        (data: Response) => {
          this.navCtrl.pop();
        }));
  }

  updateEvent() {
    this.http.put(onDeviceUrl + '/' + this.event.id, JSON.stringify(this.event), {headers: headers}).subscribe(
      (data: Response) => {
        this.navCtrl.pop();        
      }, (err) => this.http.put(backendUrl + '/' + this.event.id, JSON.stringify(this.event), {headers: headers}).subscribe(
        (data: Response) => {
          this.navCtrl.pop();
        }));
  }

  popView() {
    this.navCtrl.pop();
  }

}
