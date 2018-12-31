#!/usr/bin/python

import pprint
import requests
import sys

pp = pprint.PrettyPrinter(indent=4)
EMBYSVRIP = "" #Need to add your Emby Server IP here
USERID = "" #Need to add your own emby ID here
API_KEY = "" #Need to add your Emby API Key here
EMBYLIBID = "" #Need to add your Emby Library Parent ID here
HOSTADDR = "http://" + EMBYSVRIP + ":8096"
API_KEY = "" #Need to add your Emby API Key here
ENDPOINT = "/emby/Users/" + USERID + "/Items?" + EMBYLIBID + "&api_key=" + API_KEY
URL = HOSTADDR + ENDPOINT

r = requests.get(url = URL)
data = r.json()
#pp.pprint(data)

for i in data["Items"]:
  ENDPOINT2 = "/emby/Users/" + USERID + "/Items/" + i["Id"] + "?api_key=" + API_KEY
  URL2 = HOSTADDR + ENDPOINT2
  r = requests.get(url = URL2)
  data = r.json()
  path = data['MediaSources'][0]['Path'].split('/')[-1:][0].replace('.mp4','')
  id = data['Id']

  print "searching for " + path
  print(id)

  with open("vudu_movie_list.csv") as search:
    for line in search:
      line = line.rstrip()  # remove '\n' at end of line
      if line.startswith(path):
        vudu_content_id = line.split(',')[-1:][0]
        Tag = "vudu-" + vudu_content_id
        #print data["Tags"]
        if not Tag in data["Tags"]:
          data["Tags"] = Tag
          #pp.pprint(data)
          ENDPOINT3 = "/emby/Items/" + id + "?api_key="
          URL3 = HOSTADDR + ENDPOINT3 + API_KEY
          print data["Tags"]
          r = requests.post(URL3, json=data)
          print r.status_code
        else:
          print str(data["Tags"][0]) + " already exists"
