import urllib.request
import json
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

invidious_instances = [
    'https://invidious.nerdvpn.de',
    'https://inv.tux.pizza',
    'https://vid.priv.au',
    'https://invidious.jing.rocks',
    'https://invidious.drgns.space',
    'https://yt.artemislena.eu',
    'https://invidious.io.lol',
    'https://iv.melmac.space'
]

piped_instances = [
    'https://pipedapi.kavin.rocks',
    'https://api.piped.privacydev.net',
    'https://pipedapi.tokhmi.xyz',
    'https://piped-api.lunar.icu'
]

print("--- Testing Invidious Instances ---")
for inst in invidious_instances:
    try:
        url = f"{inst}/api/v1/search?q=The+Weeknd+Blinding+Lights&type=video"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=6, context=ctx) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            if data and isinstance(data, list):
                first = data[0]
                vid_id = first.get("videoId")
                title = first.get("title")
                print(f"[OK] Invidious {inst} -> {title} (ID: {vid_id})")
                
                # Check video info & audio stream
                vid_url = f"{inst}/api/v1/videos/{vid_id}"
                req2 = urllib.request.Request(vid_url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req2, timeout=6, context=ctx) as resp2:
                    vid_data = json.loads(resp2.read().decode('utf-8'))
                    formats = vid_data.get('adaptiveFormats', [])
                    audio_fmts = [f for f in formats if 'audio' in f.get('type', '')]
                    print(f"     Found {len(audio_fmts)} audio streams! Length: {vid_data.get('lengthSeconds')}s")
                    if audio_fmts:
                        sample = audio_fmts[0]
                        print(f"     Direct Audio URL: {sample.get('url', '')[:80]}...")
                        # Also check latest_version proxy URL
                        proxy_url = f"{inst}/latest_version?id={vid_id}&itag=140"
                        print(f"     Proxy Audio URL: {proxy_url}")
    except Exception as e:
        print(f"[FAIL] Invidious {inst}: {e}")

print("\n--- Testing Piped Instances ---")
for inst in piped_instances:
    try:
        url = f"{inst}/search?q=The+Weeknd+Blinding+Lights&filter=music_songs"
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=6, context=ctx) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            items = data.get("items", [])
            print(f"[OK] Piped {inst} -> found {len(items)} items")
            if items:
                first = items[0]
                print(f"     First: {first.get('title')} ({first.get('url')})")
                url_path = first.get('url') # e.g. /watch?v=...
                vid_id = url_path.replace('/watch?v=', '')
                stream_url = f"{inst}/streams/{vid_id}"
                req2 = urllib.request.Request(stream_url, headers={'User-Agent': 'Mozilla/5.0'})
                with urllib.request.urlopen(req2, timeout=6, context=ctx) as resp2:
                    stream_data = json.loads(resp2.read().decode('utf-8'))
                    audio_streams = stream_data.get("audioStreams", [])
                    print(f"     Found {len(audio_streams)} audioStreams! Length: {stream_data.get('duration')}s")
                    if audio_streams:
                        print(f"     Audio stream URL: {audio_streams[0].get('url')[:80]}...")
    except Exception as e:
        print(f"[FAIL] Piped {inst}: {e}")
