import { Component, Input, Output, EventEmitter, OnDestroy, OnInit } from '@angular/core';
import { TicketsService, EventSubscription, UserTicketSummary, TicketMessage, Ticket } from '../../app/tickets.service';
import { formatCurrentMoment } from '../../app/utils';
import { Vibration } from '@ionic-native/vibration';
import { Subscription } from 'rxjs';
import { Observable } from 'rxjs/Observable';
import { BackgroundMode } from '@ionic-native/background-mode';
import { LocalNotifications } from '@ionic-native/local-notifications';
import { Toast } from '@ionic-native/toast';
import { Platform } from 'ionic-angular';
import { TranslateService } from 'ng2-translate';

@Component({
  selector: 'ticket',
  templateUrl: 'ticket.html'
})
export class TicketComponent implements OnInit, OnDestroy {

  ticketSummary: UserTicketSummary;
  lastMessageTitle: string;
  lastMessage: string;
  cancelled: boolean;
  
  get called(): boolean {
    return this.subscribedEvent.ticket.state === 'Called';
  }

  get accepted(): boolean {
    return this.subscribedEvent.ticket.state === 'Confirmed';
  }

  get eventTitle() {
    return this.subscribedEvent ? this.subscribedEvent.description : '';
  }

  get subscribedEventParticipants() {
    return this.subscribedEvent.ticket.participants;
  }
  get connectedState(): Observable<boolean> {
    return this.ws.connected;
  }

  get loggedIn(): Observable<boolean> {
    return this.ws.identified;
  }

  private summariesSubsription: Subscription;
  private acceptedSubsription: Subscription;
  private calledSubsription: Subscription;
  private activatedSubsription: Subscription;
  private closedSubscription: Subscription;
  private expiredSubscription: Subscription;
  private skippedSubscription: Subscription;

  constructor(private ws: TicketsService, private vibration :Vibration, public platform: Platform,
    private backgroundMode: BackgroundMode, private localNotifications: LocalNotifications, private toast: Toast,
    private translate: TranslateService) {
      
  }

  private showTostMessage() {
    if (this.platform.is('cordova')) {
      try {
        this.toast.showLongBottom(this.lastMessage).subscribe();
      } catch (e) {
  
      }
    }
  }

  ngOnInit(): void {
    this.lastMessageTitle = this.translate.instant('messages.TicketRegistered', {"moment": formatCurrentMoment()});
    this.lastMessage = this.translate.instant("messages.You'll be called", {"eventTitle": this.eventTitle});
    this.cancelled = false;
    const filterMyTicketChannel = (msg: TicketMessage) => msg && msg.ticket.id === this.subscribedEvent.ticket.id && msg.ticket.eventid == this.subscribedEvent.ticket.eventid;
    const filterMyTicketSummaryChannel = (summary: UserTicketSummary) => summary && summary.eventid === this.subscribedEvent.ticket.eventid;

    this.activatedSubsription = this.ws.ticketActivated.filter(filterMyTicketChannel).subscribe(msg => {
      this.subscribedEvent.ticket = msg.ticket;
      this.lastMessageTitle = this.translate.instant('messages.TicketReactivated', {"moment": formatCurrentMoment()});
      this.lastMessage = this.translate.instant("messages.You'll be called", {"eventTitle": this.eventTitle});
      this.showTostMessage();
    });
    this.calledSubsription = this.ws.ticketCalled.filter(filterMyTicketChannel).subscribe(msg =>{
      this.subscribedEvent.ticket = msg.ticket;
      this.vibration.vibrate([1000 , 1000 , 500, 500, 1000]);
      this.lastMessageTitle = this.translate.instant('messages.LetsGo', {"moment": formatCurrentMoment()});
      this.lastMessage = this.translate.instant("Please confirm");
      if (this.platform.is('cordova')) {
        try {
          this.localNotifications.schedule({
            id: msg.ticket.eventid,
            text: this.lastMessage
          });
          this.backgroundMode.isScreenOff().then((off: boolean) => {
            try {
              if (off) {
                this.backgroundMode.unlock();
              } else {
                this.backgroundMode.moveToForeground();
              }
            } catch (e) {}
          });
        } catch (e) {}
      }
    });
    this.acceptedSubsription = this.ws.ticketAccepted.filter(filterMyTicketChannel).subscribe(msg => {
      this.subscribedEvent.ticket = msg.ticket;
      this.lastMessageTitle = this.translate.instant('messages.TicketAccepted', {"moment": formatCurrentMoment()});
      this.lastMessage = this.translate.instant('messages.We expect you', {"eventTitle": this.eventTitle});
      this.showTostMessage();
    });
    this.skippedSubscription = this.ws.ticketSkipped.filter(filterMyTicketChannel).subscribe(msg => {
      this.subscribedEvent.ticket = msg.ticket;
      this.lastMessageTitle = this.translate.instant('messages.TicketSkipped', {"moment": formatCurrentMoment()});
      this.lastMessage = this.translate.instant('messages.You will be called next Iteration', {"eventTitle": this.eventTitle});
      this.showTostMessage();
    });
    this.expiredSubscription = this.ws.ticketExpired.filter(filterMyTicketChannel).subscribe(msg => {
      this.subscribedEvent.ticket = msg.ticket;
      this.lastMessageTitle = this.translate.instant('messages.TicketExpired', {"moment": formatCurrentMoment()});
      this.lastMessage = this.translate.instant('messages.You will be called next Iteration', {"eventTitle": this.eventTitle});
      this.showTostMessage();
    });
    this.closedSubscription = this.ws.ticketDeleted.filter(filterMyTicketChannel).subscribe(msg => {
      this.subscribedEvent.ticket = msg.ticket;
      this.cancelled = true;
      this.lastMessageTitle = this.translate.instant('messages.TicketReturned', {"moment": formatCurrentMoment()});
      this.lastMessage = this.translate.instant("messages.You're no longer waiting", {"eventTitle": this.eventTitle});
      this.showTostMessage();
    });
    this.summariesSubsription = this.ws.ticketSummaries.filter(filterMyTicketSummaryChannel).subscribe((summary: UserTicketSummary) => {
      this.ticketSummary = summary;
    });    
  }

  @Input()
  subscribedEvent: EventSubscription;

  @Input()
  showActions: false;

  @Output()
  onClose = new EventEmitter<Ticket>();

  skip(slidingItem) {
    this.lastMessageTitle = this.translate.instant('messages.NotReadyYet', {"moment": formatCurrentMoment()});
    this.lastMessage = this.translate.instant('messages.I Skipped');
    this.ws.skip(this.subscribedEvent.ticket);
    slidingItem.close();
  }

  accept(slidingItem) {
    this.lastMessageTitle = this.translate.instant('messages.Confirmed', {"moment": formatCurrentMoment()});
    this.lastMessage = this.translate.instant("messages.Yes, i'll be there");
    this.ws.confirm(this.subscribedEvent.ticket);
    slidingItem.close();
  }

  cancel(slidingItem) {
    this.ws.unsubscribeEvent(this.subscribedEvent.ticket.eventid);
    slidingItem.close();
  }

  close(slidingItem) {
    this.onClose.next(this.subscribedEvent.ticket);   
    slidingItem.close(); 
  }

  toggleActions() {
    this.showActions != this.showActions;
  }

  ngOnDestroy(): void {
    this.clearSubsriptions();
  }

  clearSubsriptions() {
    if (this.summariesSubsription) {
      this.summariesSubsription.unsubscribe();
      this.acceptedSubsription.unsubscribe();
      this.calledSubsription.unsubscribe();
      this.activatedSubsription.unsubscribe();
      this.closedSubscription.unsubscribe();
      this.expiredSubscription.unsubscribe();
      this.skippedSubscription.unsubscribe();
    }
  }
}
