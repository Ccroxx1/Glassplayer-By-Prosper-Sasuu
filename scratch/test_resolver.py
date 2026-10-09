import urllib.request, json, ssl

key = 'db688ed599msh91af8b1ceff2dcep14dab6jsn84a17c45d461'
host = 'ytstream-download-youtube-videos.p.rapidapi.com'
ctx = ssl._create_unverified_context()

def resolve_audio_stream(video_id):
    url = f"https://{host}/dl?id={video_id}"
    req = urllib.request.Request(url, headers={'x-rapidapi-key': key, 'x-rapidapi-host': host})
    with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
        d = json.loads(r.read().decode())
        formats = d.get('adaptiveFormats', [])
        audios = [f for f in formats if 'audio' in f.get('mimeType', '')]
        if not audios:
            return None
        # Prefer itag 140 (AAC) or 251 (Opus)
        best = next((a for a in audios if a.get('itag') == 140), audios[0])
        initial_url = best.get('url')
        
        # Follow redirect if it's redirector.googlevideo.com
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def http_error_302(self, req, fp, code, msg, headers):
                return headers.get('Location')
        
        opener = urllib.request.build_opener(NoRedirect)
        try:
            req2 = urllib.request.Request(initial_url, headers={'User-Agent': 'Mozilla/5.0'})
            res = opener.open(req2, timeout=8)
            # If redirected, res is string or HTTPResponse
            final_url = res if isinstance(res, str) else initial_url
            return final_url
        except Exception as e:
            return initial_url

stream = resolve_audio_stream("UxxajLWwzqY")
print("Resolved stream:", stream[:100] if stream else "None")
