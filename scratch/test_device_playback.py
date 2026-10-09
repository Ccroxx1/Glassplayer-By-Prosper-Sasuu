import urllib.request, json, ssl, subprocess

key = 'db688ed599msh91af8b1ceff2dcep14dab6jsn84a17c45d461'
host = 'ytstream-download-youtube-videos.p.rapidapi.com'
ctx = ssl._create_unverified_context()

url = f"https://{host}/dl?id=UxxajLWwzqY"
req = urllib.request.Request(url, headers={'x-rapidapi-key': key, 'x-rapidapi-host': host})
with urllib.request.urlopen(req, timeout=10, context=ctx) as r:
    d = json.loads(r.read().decode())
    af = [f for f in d.get('adaptiveFormats', []) if 'audio' in f.get('mimeType', '')]
    if af:
        audio_url = af[0].get('url')
        print("Got audio stream URL:", audio_url[:80])
        # Test fetching header from device using adb
        adb = r"C:\Users\Sasuu\AppData\Local\Android\Sdk\platform-tools\adb.exe"
        cmd = [adb, "shell", "curl", "-sIL", audio_url]
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        lines = [line for line in res.stdout.splitlines() if line.startswith("HTTP/") or "content-type" in line.lower() or "content-length" in line.lower()]
        print("Device response headers:")
        for l in lines[:8]:
            print("  ", l)
