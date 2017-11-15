import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { enableProdMode } from '@angular/core';
import { AppModule } from './app.module';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';


enableProdMode();

platformBrowserDynamic().bootstrapModule(AppModule);
