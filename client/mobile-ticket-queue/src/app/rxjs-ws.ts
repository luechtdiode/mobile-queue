import { Subject, Observer, Observable, Subscription } from 'rxjs';
import { WebSocketSubject, WebSocketSubjectConfig } from 'rxjs/observable/dom/WebSocketSubject';

export class RxWebsocketSubject<T> extends Subject<T> {
  private reconnectionObservable: Observable<number>;
  private wsSubjectConfig: WebSocketSubjectConfig;
  private socket: WebSocketSubject<any>;
  private explicitClosed = false;
  private connectionObserver: Observer<boolean>;
  public connectionStatus: Observable<boolean>;


  /// by default, when a message is received from the server, we are trying to decode it as JSON
  /// we can override it in the constructor
  defaultResultSelector = (e: MessageEvent) => {
    return e; // JSON.parse(e.data);
  }

  /// when sending a message, we encode it to JSON
  /// we can override it in the constructor
  defaultSerializer = (data: any): string => {
    return data; // JSON.stringify(data);
  }

  constructor(
    private url: string,
    private reconnectInterval: number = 5000,  /// pause between connections
    private reconnectAttempts: number = 7200,  /// number of connection attempts

    private resultSelector?: (e: MessageEvent) => any,
    private serializer?: (data: any) => string,
    ) {
    super();

    /// connection status
    this.connectionStatus = new Observable<boolean>((observer) => {
      this.connectionObserver = observer;
    }).share().distinctUntilChanged();

    if (!resultSelector) {
      this.resultSelector = this.defaultResultSelector;
    }
    if (!this.serializer) {
      this.serializer = this.defaultSerializer;
    }

    /// config for WebSocketSubject
    /// except the url, here is closeObserver and openObserver to update connection status
    this.wsSubjectConfig = {
      url: url,
      resultSelector: this.resultSelector,
      closeObserver: {
        next: (e: CloseEvent) => {
          console.log('closeObserver');
          this.socket = null;
          this.connectionObserver.next(false);
        }
      },
      openObserver: {
        next: (e: Event) => {
          console.log('openObserver');
          this.connectionObserver.next(true);
        }
      }
    };
    /// we connect
    this.connect();
    /// we follow the connection status and run the reconnect while losing the connection
    this.connectionStatus.subscribe((isConnected) => {
      if (!this.reconnectionObservable && typeof(isConnected) == "boolean" && !isConnected && !this.explicitClosed) {
        this.reconnect();
      } else if (typeof(isConnected) == "boolean" && !isConnected) {

      }
    });
  }

  connect(): void {
    this.explicitClosed = false;
    this.socket = new WebSocketSubject(this.wsSubjectConfig);
    this.socket.subscribe(
      (m) => {
        this.next(m); /// when receiving a message, we just send it to our Subject
      },
      (error: Event) => {
        if (!this.socket && !this.explicitClosed) {
          /// in case of an error with a loss of connection, we restore it
          this.reconnect();
        }
      });
  }

  close(): void {
    this.explicitClosed = true;
    if (this.socket) {
      this.socket.unsubscribe();
    }
  }

  /// reconnection
  reconnect(): void {
    console.log('start try reconnection ...');
    this.reconnectionObservable = Observable.interval(this.reconnectInterval)
      .takeWhile((v, index) => {
        return index < this.reconnectAttempts && !this.socket && !this.explicitClosed
    });
    const subsr = this.reconnectionObservable.subscribe(
      () => {
        console.log('continue with reconnection ...');
        this.connect();
      },
      null,
      () => {
        /// if the reconnection attempts are failed, then we call complete of our Subject and status
        this.reconnectionObservable = null;
        subsr.unsubscribe();
        if (!this.socket) {
          this.complete();
          this.connectionObserver.complete();
          console.log('finish with reconnection (unsuccessfull)');
        } else {
          console.log('finish with reconnection (successfull)');
        }
      });
  }

  /// sending the message
  send(data: any): void {
    this.socket.next(this.serializer(data));
  }
}