import { BrowserModule } from '@angular/platform-browser';
import { ErrorHandler, NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';

import { IonicApp, IonicErrorHandler, IonicModule } from 'ionic-angular';
import { BackgroundMode } from '@ionic-native/background-mode';
import { LocalNotifications } from '@ionic-native/local-notifications';
import { Toast } from '@ionic-native/toast';

import { MyApp } from './app.component';
import { HomePage } from '../pages/home/home';

import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';
import { Vibration } from '@ionic-native/vibration';
import { TicketsService } from './tickets.service';
import { SettingsPage } from '../pages/settings/settings';
import { TicketComponent } from '../components/ticket/ticket';
import { SubscribePage } from '../pages/subscribe/subscribe';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { MyeventsPageModule } from '../pages/myevents/myevents.module';

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
    MyeventsPageModule
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
    BackgroundMode,
    LocalNotifications,
    Toast,
    {provide: ErrorHandler, useClass: IonicErrorHandler}
  ]
})
export class AppModule {}
