import { Component } from '@angular/core';
import { NavController, NavParams } from 'ionic-angular';
import { formatCurrentMoment } from '../../app/utils';
import { TicketsService } from '../../app/tickets.service';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'page-settings',
  templateUrl: 'settings.html',
})
export class SettingsPage {

  get lastMessages():string[] {
    return this.ws.lastMessages;
  };

  constructor(public navCtrl: NavController, public ws: TicketsService) {
    // ws.logMessages.subscribe(msg => {
    //   console.log(msg);
    //   // this.lastMessages.push(formatCurrentMoment(true) + ` - ${msg}`);
    //   // this.lastMessages = this.lastMessages.slice(Math.max(this.lastMessages.length - 10, 0));
    // });
  }

  ionViewDidLoad() {
    console.log('ionViewDidLoad SettingsPage');
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

  stopped() {
    return this.ws.stopped;
  }

  loggedInText(): Observable<any> {
    return this.ws.identified.map(c => c ? `User ${this.ws.getUsername()} Connected` : `User ${this.ws.getUsername()} Disconnected`);
  }

  logIn(name) {
    this.ws.login(name);
  }

  logOut() {
    this.ws.disconnectWS();
  }
  
}
