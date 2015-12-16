package httpserver

import (
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
)

// staticOptions is a struct for specifying configuration options for the martini.Static middleware.
type staticOptions struct {
	// Prefix is the optional prefix used to serve the static directory content
	Prefix string
	// SkipLogging will disable [Static] log messages when a static file is served.
	SkipLogging bool
	// IndexFile defines which file to serve as index if it exists.
	IndexFile string
	// Expires defines which user-defined function to use for producing a HTTP Expires Header
	// https://developers.google.com/speed/docs/insights/LeverageBrowserCaching
	Expires func() string
	// Fallback defines a default URL to serve when the requested resource was
	// not found.
	Fallback string
	// Exclude defines a pattern for URLs this handler should never process.
	Exclude string
}

func preparestaticOptions(options []staticOptions) staticOptions {
	var opt staticOptions
	if len(options) > 0 {
		opt = options[0]
	}

	// Defaults
	if len(opt.IndexFile) == 0 {
		opt.IndexFile = "index.html"
	}
	// Normalize the prefix if provided
	if opt.Prefix != "" {
		// Ensure we have a leading '/'
		if opt.Prefix[0] != '/' {
			opt.Prefix = "/" + opt.Prefix
		}
		// Remove any trailing '/'
		opt.Prefix = strings.TrimRight(opt.Prefix, "/")
	}
	opt.SkipLogging = true
	return opt
}

func handler(res http.ResponseWriter, req *http.Request, directory string, staticOpt ...staticOptions) {
	Root, _ := os.Getwd()

	if !filepath.IsAbs(directory) {
		directory = filepath.Join(Root, directory)
	}
	dir := http.Dir(directory)
	opt := preparestaticOptions(staticOpt)

	if req.Method != "GET" && req.Method != "HEAD" {
		return
	}
	if opt.Exclude != "" && strings.HasPrefix(req.URL.Path, opt.Exclude) {
		return
	}
	file := req.URL.Path
	// if we have a prefix, filter requests by stripping the prefix
	if opt.Prefix != "" {
		if !strings.HasPrefix(file, opt.Prefix) {
			return
		}
		file = file[len(opt.Prefix):]
		if file != "" && file[0] != '/' {
			return
		}
	}
	f, err := dir.Open(file)
	if err != nil {
		// try any fallback before giving up
		if opt.Fallback != "" {
			file = opt.Fallback // so that logging stays true
			f, err = dir.Open(opt.Fallback)
		}

		if err != nil {
			// discard the error?
			msgQueen <- fmt.Sprintf("%v", err)
			return
		}
	}
	defer f.Close()

	fi, err := f.Stat()
	if err != nil {
		return
	}

	// try to serve index file
	if fi.IsDir() {
		// redirect if missing trailing slash
		if !strings.HasSuffix(req.URL.Path, "/") {
			dest := url.URL{
				Path:     req.URL.Path + "/",
				RawQuery: req.URL.RawQuery,
				Fragment: req.URL.Fragment,
			}
			http.Redirect(res, req, dest.String(), http.StatusFound)
			return
		}

		/*
			file = path.Join(file, opt.IndexFile)
			f, err = dir.Open(file)
			if err != nil {
				return
			}
			defer f.Close()

			fi, err = f.Stat()
			if err != nil || fi.IsDir() {
				return
			}
		*/

		tree, err := ioutil.ReadDir(fmt.Sprintf("%s%s", directory, req.URL.Path))
		if err != nil {
			return
		}

		result := make([]byte, 1)
		for _, t := range tree {
			line := fmt.Sprintf("<a href='%s'>%s</a><br/>", t.Name(), t.Name())
			result = append(result, []byte(line)...)
		}

		res.Header().Set("Content-Type", "text/html")
		if len(result) < 2 {
			res.Write([]byte("Nothing..."))
		} else {
			res.Write(result)
		}
		return
	}

	// Add an Expires header to the static content
	if opt.Expires != nil {
		res.Header().Set("Expires", opt.Expires())
	}

	http.ServeContent(res, req, file, fi.ModTime(), f)
}
