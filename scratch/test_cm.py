import urllib.request, json, ssl

key = 'db688ed599msh91af8b1ceff2dcep14dab6jsn84a17c45d461'
host = 'ytstream-download-youtube-videos.p.rapidapi.com'
ctx = ssl._create_unverified_context()

for cm_val in ['true', '1', 'proxy', 'm', 'c']:
    url = f"https://{host}/dl?id=UxxajLWwzqY&cm={cm_val}"
    try:
        req = urllib.request.Request(url, headers={'x-rapidapi-key': key, 'x-rapidapi-host': host})
        with urllib.request.urlopen(req, timeout=8, context=ctx) as r:
            d = json.loads(r.read().decode())
            is_proxied = d.get("isProxied")
            print(f"cm={cm_val} -> isProxied: {is_proxied}")
            formats = d.get('adaptiveFormats', []) or d.get('formats', [])
            if formats:
                print(f"   sample url: {formats[0].get('url')[:60]}")
    except Exception as e:
        print(f"cm={cm_val} err: {e}")
