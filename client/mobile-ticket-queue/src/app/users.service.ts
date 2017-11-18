import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { formatCurrentMoment, onDeviceUserUrl, backendUserUrl } from './utils';
import { ReplaySubject } from 'rxjs';
import { HttpClient } from '@angular/common/http';


export interface User {  
  name: string;
  deviceIds: string[];
  id: number;
  mail: string;
  mobile: string;
  password: string;
}

@Injectable()
export class UsersService {
  usernames = {};

  constructor(private http: HttpClient) {}

  getUserName(userid: number): Observable<string> {
    if (!this.usernames[userid]) {
      const userPromise = new BehaviorSubject('#' + userid);
      this.usernames[userid] = userPromise;
      this.http.get(onDeviceUserUrl + '/' + userid).subscribe(
        (data: User) => {
          userPromise.next(data.name);
        }, (err) => this.http.get(backendUserUrl + '/' + userid).subscribe(
          (data: User) => {
            userPromise.next(data.name)
          }));
      return userPromise;
    } else {
      return this.usernames[userid];
    }
  }
}
