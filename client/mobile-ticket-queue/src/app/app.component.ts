import { Component, ViewChild } from '@angular/core';
import { Nav, Platform } from 'ionic-angular';
import { StatusBar } from '@ionic-native/status-bar';
import { SplashScreen } from '@ionic-native/splash-screen';
import { Globalization } from '@ionic-native/globalization';
import { TranslateService } from '@ngx-translate/core';

import { HomePage } from '../pages/home/home';
import { TicketsService } from './tickets.service';
import { SettingsPage } from '../pages/settings/settings';
import { LocalNotifications } from '@ionic-native/local-notifications';
import { MyeventsPage } from '../pages/myevents/myevents';
import { defaultLanguage, availableLanguages, sysOptions } from './utils';

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
    public backendWS: TicketsService, 
    private translate: TranslateService,
    private globalization: Globalization,
    private localNotifications: LocalNotifications) {

    this.initializeApp();

  }
	getSuitableLanguage(language) {
    language = language.substring(0, 2).toLowerCase();
    return availableLanguages.some(x => x.code == language) ? language : defaultLanguage;
  }

  initializeApp() {
    this.platform.ready().then(() => {
      // Okay, so the platform is ready and our plugins are available.
      // Here you can do any higher level native things you might need.
      this.statusBar.styleDefault();

      // this language will be used as a fallback when a translation isn't found in the current language
      this.translate.setDefaultLang(defaultLanguage);
      if ((<any>window).cordova) {
        this.globalization.getPreferredLanguage().then(result => {
          var language = this.getSuitableLanguage(result.value);
          this.translate.use(language);
          sysOptions.systemLanguage = language;
        });
      } else {
        let browserLanguage = this.translate.getBrowserLang() || defaultLanguage;
        var language = this.getSuitableLanguage(browserLanguage);
        this.translate.use(language);
        sysOptions.systemLanguage = language;
      }
      this.translate.onLangChange.subscribe(() => {
        this.updateMenuWithCurrentLang();
      });
      this.updateMenuWithCurrentLang();
      this.splashScreen.hide();
      if (this.platform.is('cordova')) {
        this.localNotifications.registerPermission();
      }
    });
  }

  updateMenuWithCurrentLang() {
    this.translate.get('loadAllTextsInCurrentLanguage').subscribe(text => {
      this.pages = [
        { title: 'Home', component: HomePage },
        { title: this.translate.instant('texts.Setup'), component: SettingsPage },
         { title: this.translate.instant('texts.MyEvents'), component: MyeventsPage },
      ];  
    });    
  }
  openPage(page) {
    if (this.nav.getActive().component === page.component) {
      return;
    }
    // Reset the content nav to have just this page
    // we wouldn't want the back button to show in this scenario
    if (page.component === SettingsPage) {
      this.nav.push(page.component);      
    // } else if (page.component === MyeventsPage) {
    //   this.nav.push(page.component);      
    } else {
      this.nav.setRoot(page.component);
    }
  }
}
