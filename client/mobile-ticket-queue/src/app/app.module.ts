import { BrowserModule } from '@angular/platform-browser';
import { ErrorHandler, NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';

import { IonicApp, IonicErrorHandler, IonicModule } from 'ionic-angular';

import { MyApp } from './app.component';
import { HomePage } from '../pages/home/home';

import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';
import { Vibration } from '@ionic-native/vibration';
import { TicketsService } from './tickets.service';
import 'rxjs/Rx';
import { SettingsPage } from '../pages/settings/settings';
import { TicketComponent } from '../components/ticket/ticket';
import { SubscribePage } from '../pages/subscribe/subscribe';

@NgModule({
  declarations: [
    MyApp,
    TicketComponent,
    HomePage,
    SettingsPage,
    SubscribePage,
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    IonicModule.forRoot(MyApp),
  ],
  bootstrap: [IonicApp],
  entryComponents: [
    MyApp,
    HomePage,
    SubscribePage,
    SettingsPage,
  ],
  providers: [
    StatusBar,
    SplashScreen,
    TicketsService,
    Vibration,
    {provide: ErrorHandler, useClass: IonicErrorHandler}
  ]
})
export class AppModule {}
