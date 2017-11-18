import { NgModule } from '@angular/core';
import { IonicPageModule } from 'ionic-angular';
import { MyeventsPage } from './myevents';

@NgModule({
  declarations: [
    MyeventsPage,
  ],
  imports: [
    IonicPageModule.forChild(MyeventsPage),
  ],
})
export class MyeventsPageModule {}
