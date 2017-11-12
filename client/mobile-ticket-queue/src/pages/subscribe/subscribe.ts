import { Component } from '@angular/core';
import { NavController, NavParams } from 'ionic-angular';
import { TicketsService, Event } from '../../app/tickets.service';

@Component({
  selector: 'page-subscribe',
  templateUrl: 'subscribe.html',
})
export class SubscribePage {

  event: Event;

  countModel = 1;

  constructor(public navCtrl: NavController, public navParams: NavParams, public ws: TicketsService) {
    this.event = navParams.get('event');
  }

  registerTicketForEvent(count) {
    this.ws.subscribeEvent(this.event.id, count);
    this.navCtrl.pop();
  }

  popView() {
    this.navCtrl.pop();
  }

}
