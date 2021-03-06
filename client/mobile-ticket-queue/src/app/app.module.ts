import { BrowserModule } from '@angular/platform-browser';
import { ErrorHandler, NgModule } from '@angular/core';
import { HttpClientModule, HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { HTTP_INTERCEPTORS } from '@angular/common/http';

import { IonicApp, IonicErrorHandler, IonicModule } from 'ionic-angular';
import { BackgroundMode } from '@ionic-native/background-mode';
import { LocalNotifications } from '@ionic-native/local-notifications';
import { Toast } from '@ionic-native/toast';
import { QRCodeModule } from 'angular2-qrcode';

import { MyApp } from './app.component';
import { HomePage } from '../pages/home/home';

import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';
import { Vibration } from '@ionic-native/vibration';
import { TicketsService } from './tickets.service';
import { SettingsPage } from '../pages/settings/settings';
import { TicketComponent } from '../components/ticket/ticket';
import { SubscribePage } from '../pages/subscribe/subscribe';
import { MyeventsPage } from '../pages/myevents/myevents';
import { Globalization } from '@ionic-native/globalization';
import { UsersService } from './users.service';
import { EventAdminPage } from '../pages/eventadmin/eventadmin';
import { ProgressBarComponent } from '../components/progressbar/progress-bar';
import { AddEventPage } from '../pages/addevent/addevent';
import { TokenInterceptor } from './token-interceptor';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

@NgModule({
  declarations: [
    MyApp,
    TicketComponent,
    ProgressBarComponent,
    HomePage,
    SettingsPage,
    SubscribePage,
    MyeventsPage,
    EventAdminPage,
    AddEventPage
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    IonicModule.forRoot(MyApp),
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [HttpClient]
      }
    }),
    QRCodeModule
  ],
  bootstrap: [IonicApp],
  entryComponents: [
    MyApp,
    HomePage,
    SubscribePage,
    SettingsPage,
    MyeventsPage,
    EventAdminPage,
    AddEventPage
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
    {
      provide: HTTP_INTERCEPTORS,
      useClass: TokenInterceptor,
      multi: true
    },
    {provide: ErrorHandler, useClass: IonicErrorHandler}
  ]
})
export class AppModule {}
