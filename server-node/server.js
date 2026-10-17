const express = require("express");
const path = require("path");

const app = express();
const PORT = Number(process.env.PORT || 3000);
const JAVA_API = process.env.JAVA_API_URL || "http://localhost:8081";
const publicDir = path.join(__dirname, "..", "frontend");

app.disable("x-powered-by");
app.use(express.json({ limit: "32kb" }));
app.use(express.urlencoded({ extended: false, limit: "32kb" }));

app.get("/api/health", async (req, res) => {
  try {
    const response = await fetch(`${JAVA_API}/api/health`);
    res.status(response.status).type("json").send(await response.text());
  } catch (error) {
    res.status(502).json({ error: "Java API is unavailable." });
  }
});

app.use("/api", async (req, res) => {
  const target = `${JAVA_API}/api${req.path}${req.url.includes("?") ? req.url.slice(req.url.indexOf("?")) : ""}`;
  try {
    const headers = { "Content-Type": req.get("content-type") || "application/x-www-form-urlencoded" };
    if (req.headers.cookie) headers.Cookie = req.headers.cookie;
    const init = { method: req.method, headers };
    if (!["GET", "HEAD"].includes(req.method)) {
      init.body = req.is("application/json") ? JSON.stringify(req.body) : new URLSearchParams(req.body).toString();
    }
    const response = await fetch(target, init);
    const setCookie = response.headers.get("set-cookie");
    if (setCookie) res.set("set-cookie", setCookie);
    res.status(response.status);
    res.type(response.headers.get("content-type") || "application/json");
    res.send(await response.text());
  } catch (error) {
    res.status(502).json({ error: "Unable to reach the Java API." });
  }
});

app.use(express.static(publicDir, { extensions: ["html"] }));
app.get("*", (req, res) => res.sendFile(path.join(publicDir, "index.html")));

app.listen(PORT, () => {
  console.log(`Parking gateway listening on http://localhost:${PORT}`);
  console.log(`Proxying Java API requests to ${JAVA_API}`);
});
