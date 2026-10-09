import urllib.request, json, ssl, re

ctx = ssl._create_unverified_context()

def search_piped(query):
    try:
        url = f"https://api.piped.private.coffee/search?q={urllib.parse.quote(query)}&filter=music_songs"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=6, context=ctx) as r:
            d = json.loads(r.read().decode())
            items = d.get('items', [])
            print(f"Piped found {len(items)} items")
            if items:
                first = items[0]
                vid = first.get('url', '').replace('/watch?v=', '')
                print(f"  First: {first.get('title')} ({vid})")
                return vid
    except Exception as e:
        print("Piped search err:", e)
    return None

def search_youtubei(query):
    try:
        req = urllib.request.Request(
            'https://www.youtube.com/youtubei/v1/search?prettyPrint=false',
            data=json.dumps({
                'context': {'client': {'clientName': 'ANDROID', 'clientVersion': '19.09.37', 'androidSdkVersion': 30, 'hl': 'en', 'gl': 'US'}},
                'query': query
            }).encode('utf-8'),
            headers={
                'Content-Type': 'application/json',
                'User-Agent': 'com.google.android.youtube/19.09.37 (Linux; U; Android 11)'
            }
        )
        with urllib.request.urlopen(req, timeout=6, context=ctx) as r:
            raw = r.read().decode('utf-8')
            matches = re.findall(r'"videoId":"([a-zA-Z0-9_-]{11})"', raw)
            print(f"YouTubeI found {len(matches)} videoIds")
            if matches:
                print(f"  First ID: {matches[0]}")
                return matches[0]
    except Exception as e:
        print("YouTubeI search err:", e)
    return None

v1 = search_piped("The Weeknd Blinding Lights")
v2 = search_youtubei("The Weeknd Blinding Lights")

# Now test YTStream DL endpoint with this videoId
target_id = v1 or v2 or "UxxajLWwzqY"
key = 'db688ed599msh91af8b1ceff2dcep14dab6jsn84a17c45d461'
host = 'ytstream-download-youtube-videos.p.rapidapi.com'
dl_url = f"https://{host}/dl?id={target_id}"
req = urllib.request.Request(dl_url, headers={'x-rapidapi-key': key, 'x-rapidapi-host': host})
with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
    data = json.loads(r.read().decode())
    print("YTStream dl title:", data.get('title'))
    print("Duration:", data.get('lengthSeconds'), "seconds")
    audios = [f for f in data.get('adaptiveFormats', []) if 'audio' in f.get('mimeType', '')]
    print(f"Found {len(audios)} audio formats")
    if audios:
        print("Sample Audio URL:", audios[0].get('url')[:100])
        # Test 1 chunk
        req_stream = urllib.request.Request(audios[0].get('url'), headers={'User-Agent': 'Mozilla/5.0', 'Range': 'bytes=0-1024'})
        with urllib.request.urlopen(req_stream, timeout=6, context=ctx) as s_resp:
            print("Stream verified! HTTP:", s_resp.status, "Bytes read:", len(s_resp.read()))
