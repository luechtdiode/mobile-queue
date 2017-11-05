import { Component, ViewChild } from '@angular/core';
import { Nav, Platform } from 'ionic-angular';
import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';

import { HomePage } from '../pages/home/home';
import { TicketsService } from './tickets.service';

@Component({
  templateUrl: 'app.html'
})
export class MyApp {
  @ViewChild(Nav) nav: Nav;

  rootPage: any = HomePage;

  pages: Array<{title: string, component: any}>;

  constructor(
    public platform: Platform, 
    public statusBar: StatusBar, 
    public splashScreen: SplashScreen, 
    public backendWS: TicketsService) {

    this.initializeApp();

    // used for an example of ngFor and navigation
    // this.pages = [
    //   { title: 'Home', component: HomePage },
    //   { title: 'Identified', component: IdentifiedPage },
    //   { title: 'Subscribed', component: IdentifiedPage },
    //   { title: 'Called', component: IdentifiedPage },
    //   { title: 'Accepted', component: IdentifiedPage },
    // ];

  }

  initializeApp() {
    this.platform.ready().then(() => {
      // Okay, so the platform is ready and our plugins are available.
      // Here you can do any higher level native things you might need.
      this.statusBar.styleDefault();
      this.splashScreen.hide();
      this.backendWS.connected.subscribe(connected => {
        console.log('connected: ' + connected);
      });
      this.backendWS.identified.subscribe(identified => {
        console.log('identified: ' + identified);
      });
      this.backendWS.ticketCreated.subscribe(ticketCreated => {
        console.log('ticketCreated: ' + ticketCreated);
      });
      this.backendWS.ticketCalled.subscribe(ticketCalled => {
        console.log('ticketCalled: ' + ticketCalled);
        // this.openPage(this.pages[1]);
      });
      this.backendWS.ticketCalled.subscribe(ticketCalled => {
        console.log('ticketCalled: ' + ticketCalled);
        // this.openPage(this.pages[2]);
      });
      
    });
  }

  // openPage(page) {
  //   // Reset the content nav to have just this page
  //   // we wouldn't want the back button to show in this scenario
  //   this.nav.setRoot(page.component);
  // }
}
