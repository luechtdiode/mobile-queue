import { BrowserModule } from '@angular/platform-browser';
import { ErrorHandler, NgModule } from '@angular/core';
import { HttpClientModule } from '@angular/common/http';
import { TranslateLoader, TranslateStaticLoader, TranslateModule, TranslateService } from 'ng2-translate';

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
import { MyeventsPage } from '../pages/myevents/myevents';
import { Http } from '@angular/http';
import { Globalization } from '@ionic-native/globalization';
import { UsersService } from './users.service';
import { EventAdminPage } from '../pages/eventadmin/eventadmin';
import { ProgressBarComponent } from '../components/progressbar/progress-bar';

@NgModule({
  declarations: [
    MyApp,
    TicketComponent,
    ProgressBarComponent,
    HomePage,
    SettingsPage,
    SubscribePage,
    MyeventsPage,
    EventAdminPage
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    IonicModule.forRoot(MyApp),
    TranslateModule.forRoot({
      provide: TranslateLoader,
      useFactory: (http: Http) => new TranslateStaticLoader(http, '/assets/i18n', '.json'),
      deps: [Http]
    })
  ],
  bootstrap: [IonicApp],
  entryComponents: [
    MyApp,
    HomePage,
    SubscribePage,
    SettingsPage,
    MyeventsPage,
    EventAdminPage
  ],
  providers: [
    Globalization,
    StatusBar,
    SplashScreen,
    TicketsService,
    UsersService,
    Vibration,
    BackgroundMode,
    LocalNotifications,
    Toast,
    {provide: ErrorHandler, useClass: IonicErrorHandler}
  ]
})
export class AppModule {}
