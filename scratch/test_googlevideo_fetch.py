import urllib.request, json, ssl

key = 'db688ed599msh91af8b1ceff2dcep14dab6jsn84a17c45d461'
host = 'ytstream-download-youtube-videos.p.rapidapi.com'
ctx = ssl._create_unverified_context()

url = f"https://{host}/dl?id=UxxajLWwzqY"
req = urllib.request.Request(url, headers={'x-rapidapi-key': key, 'x-rapidapi-host': host})
with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
    d = json.loads(r.read().decode())
    formats = d.get('adaptiveFormats', [])
    audios = [f for f in formats if 'audio' in f.get('mimeType', '')]
    if audios:
        # Check itag 140
        a140 = next((a for a in audios if a.get('itag') == 140), audios[0])
        stream_url = a140.get('url')
        print("Testing stream URL:", stream_url[:120])
        # Disable auto-redirect to see where it redirects
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def http_error_302(self, req, fp, code, msg, headers):
                print("302 Location header:", headers.get('Location'))
                return fp

        opener = urllib.request.build_opener(NoRedirect)
        try:
            req2 = urllib.request.Request(stream_url, headers={
                'User-Agent': 'com.google.android.youtube/19.09.37 (Linux; U; Android 11)'
            })
            resp = opener.open(req2, timeout=8)
            print("Response status:", resp.status)
        except Exception as e:
            print("Fetch err:", e)
