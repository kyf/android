package httpserver

import (
	"fmt"
	"net"
	"net/http"
	"sync"
	"time"
)

var (
	exit     chan string = make(chan string, 1)
	msgQueen chan string = make(chan string, 30)
)

type HttpServer struct {
	port       string
	state      int
	stateMutex sync.RWMutex
	root       string
	listener   net.Listener
}

func NewServer(port, root string) *HttpServer {
	return &HttpServer{port: port, root: root, listener: nil}
}

func logger(res http.ResponseWriter, req *http.Request) string {
	addr := req.Header.Get("X-Real-IP")
	if addr == "" {
		addr = req.Header.Get("X-Forwarded-For")
		if addr == "" {
			addr = req.RemoteAddr
		}
	}

	result := make([]byte, 10)

	str := fmt.Sprintf("[%s]%s", req.URL.Path, addr)
	result = append(result, []byte(str)...)

	return string(result)
}

type server struct {
	root string
}

func (s *server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	msgQueen <- logger(w, r)
	handler(w, r, s.root)
}

type tcpKeepAliveListener struct {
	*net.TCPListener
}

func (ln tcpKeepAliveListener) Accept() (c net.Conn, err error) {
	tc, err := ln.AcceptTCP()
	if err != nil {
		return
	}
	tc.SetKeepAlive(true)
	tc.SetKeepAlivePeriod(3 * time.Minute)
	return tc, nil
}

func (s *HttpServer) Start() string {
	s.stateMutex.Lock()
	defer s.stateMutex.Unlock()
	s.state = 1
	var result string = ""
	srv := &http.Server{Addr: fmt.Sprintf(":%s", s.port), Handler: &server{root: s.root}}

	var err error
	s.listener, err = net.Listen("tcp", srv.Addr)
	if err != nil {
		result = fmt.Sprintf("start err:%v", err)
	} else {
		err = srv.Serve(tcpKeepAliveListener{s.listener.(*net.TCPListener)})
		if err != nil {
			result = fmt.Sprintf("start err:%v", err)
		}
	}

	s.state = 0
	return result
}

func (s *HttpServer) State() int {
	return s.state
}

func (s *HttpServer) ReadMsg() string {
	return <-msgQueen
}

func (s *HttpServer) Stop() {
	s.listener.Close()
}
