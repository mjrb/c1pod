require 'geoip'
#this program reports ip usage from locations. i made it because i saw a ton of
#of different ips connecting to my server so i wanted to know where they're from and how much they connected
#it also spits out connection times on the next line

#data ips.txt is some preprocessed ip data from the Apache log
#I got it with bash $ cat /path/to/apache/access_log | cut -d ' ' -f 1,4 > ips.txt
#each line is in the format ip date-time

Ips=File.read("ips.txt").split("\n").map{|line| line.split " "}
Geo=GeoIP.new("GeoLiteCity.dat")
#group all conections from the same ip
groups=Ips.sort_by{|line| line[0]}.group_by {|line| line[0]}

#print
#IP Connection_Count : Location
#date-times

groups.map do |group|
  ip = group [0]
  all_lines=group[1]
  print ip, " "
  print all_lines.length, " : "
  loc_data=Geo.city(ip)
  if loc_data.nil?
    print " na "
  else
    print loc_data.city_name
  end
  puts
  
  #print all date-times
  all_lines.map{|line| print line[1], " "}
  puts
end

=begin
there are many bots scraping github probably trying to find vulnerable projects 
or emails to spam off of peoples personal websites. or someone is using some
kind of scramble proxy that switches ip every request. the latter is unlikely because
most seem to be getting / in the Apache log

example bot output
[IP REDACTED] 1 : West Palm Beach
[20/Jun/2018:12:23:04 
[IP REDACTED] 1 : Philadelphia
[22/Jun/2018:04:17:31 
[IP REDACTED] 1 : Arbil
[21/Jun/2018:22:17:39 
[IP REDACTED] 1 : Nanchang
[22/Jun/2018:00:40:29 
[IP REDACTED] 1 : Jinan
[21/Jun/2018:11:25:06 
[IP REDACTED] 1 : Hanoi
[21/Jun/2018:16:45:01 
[IP REDACTED] 1 : Ho Chi Minh City
[22/Jun/2018:01:17:52 
[IP REDACTED] 1 : Shanghai
[21/Jun/2018:14:53:34 
[IP REDACTED] 1 : Shanghai
[20/Jun/2018:17:45:28 
[IP REDACTED] 1 : Batu Caves
[22/Jun/2018:05:20:49 
[IP REDACTED] 2 : Beijing
[21/Jun/2018:15:04:35 [21/Jun/2018:15:04:41 
[IP REDACTED] 1 : Baihe
[21/Jun/2018:18:12:29 

you can see there are many requests from strange places and also
only one request per ip. this is explained by bots curling my main page and not getting
any of the files linked to my main page. since its a react app all they get is some HTML that
says loading and a script tag to go get the app lmao.

example browser output
[IP REDACTED] 187 : Arlington
[20/Jun/2018:14:40:17 [20/Jun/2018:14:40:17 [20/Jun/2018:14:40:17 ...<more dates>

as you can see there are much more request because the app pushes a lot of network requests
through the api proxy and I apparently deployed the unminified version of the app so it had to get
a lot of individual compiled JavaScript files. the location also makes sense because Arlington is nearby
capital one headquarters

in hind sight I would probably feel better if I configured HTTPS and added fail2ban but 
I was strapped for time and thought a self signed cert would look suspicious.
=end
