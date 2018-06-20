# c1pod

single page search engine for gpodder.net (c1pod)[https:mjrb.github.io/c1pod.html]

## Overview

a clojurescript reagent application that's searches the gpodder.net api.

###Features
- smart sorting
  + provides the client with a familiar subscription feed that shows episodes in
    chronological order. The user knows exactly the order to watch which episodes
    to avoid falling behind, because if one podcast produces faster it shows
    up more on the feed.
  + the sub feed secretly starts getting pulled asynchronously at login. episodes are  
    also sorted in place in a list so if they open the feed near login time
    results show up quick and sort themselves before the user's eyes so they don't have to
    wait for all the episodes to pull and get sorted. the user can start looking through
    their feed while later results can be loading.
- smart searching
  + results appear while the user is typing and relevant tags are displayed for the
    user to select. the search function pools multiple endpoints, top-list api-search
    and tag-searches, then sorts them all by subscribers and searches them
    for what the user is looking for.
  + with loaded searches the user's results aren't a click away,
    they're already here
- additional features
  + in the ui, the application gives the user to see more information, including all
    of the episodes on the rss feed, and the descriptions and titles for each episode.
  + the user can also download episodes from the more information menus, making it
    a practical way to find products
</salespitch>

###Technical challenges
the biggest issue had to do with platform complications. gpodder.net was intended to be
used from the python client and gpodder's own website. http basic auth causes
XHR or fetch to go into high alert because there's ways to fuzz servers with strange
requests and the last thing we want is zombie browser's attacking servers. XHR or fetch
would send a special preflight request to ask the server what they were allowed to do,
but the server would ignore the OPTIONS request and say 405 method not allowed, because
that's the behavior written in the api (only POST allowed), and the source reflects that.
this isn't normally an issue because the most popular clients don't do the requests from  
the browser, they do it from a native client or a server. I started working on a fix
by adding the correct django stuff to allow it but then i saw in api 3 they were switching 
to oauth or some kind of JWT, which will resolve the issue.
TLDR: it worked when I used curl so I decided I was going to use curl, and the easiest
way to do that was a php script. so I wrote some simple php scripts that solve this
problem and similar ones by effectively proxying the api.

### The Future
I tried to maintain as much as I could, but I'm definitely going to refactor this a bit
in the coming month(s). I'm definitely going to refactor into smaller files, because
these files aren't extremely long but it could definitely use some sprucing up. I really
enjoyed working on this project and I'd like to be able to read it easily, when I come
back to it.

### Why Clojurescript
- This is an decently sized web application that process a whole bunch of data
  and clojurescript is focused on data. core.async and things like sorted-map
  helped speed up the subscription feed a lot. (with promises it took 13s to load and
  get something on the screen)
- I just got done working on a clojurescript/reagent project and it was easier to do this
  then figure out jsx again and do things the js/react way.
- Google closure library and compiler

## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL. 

## License

Copyright © 2018 Michael J Winters

 This program is free software: you can redistribute it and/or modify
     it under the terms of the GNU General Public License as published by
         the Free Software Foundation, either version 3 of the License, or
	     (at your option) any later version.

    This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
	    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	        GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
        along with this program.  If not, see <https://www.gnu.org/licenses/>.
