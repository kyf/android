package httpserver

import (
	"fmt"
	"net/http"
)

var (
	exit     chan string = make(chan string, 1)
	msgQueen chan string = make(chan string, 30)
)

type HttpServer struct {
	port  string
	state int
	root  string
}

func NewServer(port, root string) *HttpServer {
	return &HttpServer{port: port, root: root}
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

func (s *HttpServer) Start() string {
	s.state = 1
	go func() {
		err := http.ListenAndServe(fmt.Sprintf(":%s", s.port), &server{root: s.root})
		var str string = ""
		if err != nil {
			str = fmt.Sprintf("start err:%v", err)
		}
		exit <- str
	}()

	result := <-exit
	s.state = 0
	return result
}

func (s *HttpServer) State() int {
	return s.state
}

func (s *HttpServer) ReadMsg() string {
	return <-msgQueen
}
