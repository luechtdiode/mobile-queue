import { Component } from '@angular/core';
import { NavController } from 'ionic-angular';
import { TicketsService } from '../../app/tickets.service';
import { Observable } from 'rxjs/Observable';

@Component({
  selector: 'page-settings',
  templateUrl: 'settings.html',
})
export class SettingsPage {
  subscr: any;

  get lastMessages():string[] {
    return this.ws.lastMessages;
  };

  constructor(public navCtrl: NavController, public ws: TicketsService) {
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
    this.subscr = this.ws.identified.subscribe(e => {
      if (e) {
        this.navCtrl.pop();

        this.subscr.unsubscribe();
      }
    });
    this.ws.login(name);
  }

  logOut() {
    this.ws.disconnectWS();
  }
  
}
