# Host LegalCase ERP on Oracle Cloud Always Free

## Quick path: ONE VM (your chosen option)

1. Create the account and wait for the verification e-mail (Part A below).
2. Console → **Compute → Instances → Create instance**: Ubuntu 24.04, shape
   **VM.Standard.A1.Flex, 4 OCPU / 24 GB** (or 2 / 12), public IPv4 on, save the SSH key.
3. Console → VCN → Security List → add ingress **TCP 80** and **TCP 443** from `0.0.0.0/0`.
4. SSH in (Part C), get the project (Part D), then:
   ```bash
   cd ~/legal-case-erp/deploy/oracle
   bash setup-single-vm.sh
   bash smoke-test.sh http://localhost:80     # expect: 10 passed, 0 failed
   ```
5. Open `http://<public-ip>` in a browser and sign in as `ally@mongalalaw.co.za` / `lawyer123`.

The script generates every secret itself; you type nothing except the SSH commands.
No domain yet? That is fine — plain HTTP works. Add a domain later (Part G).

> **Module note:** one VM runs three containers (web, app, database). That is a valid
> containerised deployment, but it does **not** show components on different machines.
> If your lecturer requires that, switch to the two-VM path below (Parts B4, E, F) — it
> costs one more free VM, and the same scripts support it.

---


This puts the system on **two free Oracle virtual machines**:

| Machine | Runs | Why |
|---|---|---|
| **VM 1 – app** | Backend (Java) + Caddy (HTTPS front door) | Public. This is what people open in a browser. |
| **VM 2 – database** | PostgreSQL 16 | Private. Only VM 1 can reach it. This is the "components on different machines" evidence for the module. |

Time needed: about 1.5–2 hours the first time, most of it waiting for builds.
Cost: **R0**, as long as you keep to the Always Free shapes listed below.

> You need: your own e-mail, a mobile phone for verification, a payment card for
> **identity check only** (Oracle does not charge Always Free resources), and the
> project pushed to GitHub (or the source ZIP).

---

## Part A — Create the Oracle account (you do this, in the browser)

1. Go to <https://www.oracle.com/cloud/free/> → **Start for free**.
2. Fill in the form. **Home region: choose South Africa (Johannesburg)** if it is offered,
   otherwise Frankfurt. The home region cannot be changed later.
3. Verify your e-mail and phone. Wait for the "account is ready" e-mail (can take a few minutes; sometimes up to a day).

If Oracle refuses the sign-up, the free tier is not available to your card or country.
Then use the **Render + Neon** route in `docs/DEPLOYMENT_FREE_HOSTING.md` instead.

---

## Part B — Create the network and two machines (Oracle console)

### B1. Virtual cloud network (the private network between the two VMs)
1. Console menu ☰ → **Networking → Virtual Cloud Networks** → **Start VCN Wizard**.
2. Choose **Create VCN with Internet Connectivity** → **Next**.
3. Name it `legalcase-vcn` → **Next** → **Create**.

### B2. Open the public web ports (on the public subnet's security list)
1. Open `legalcase-vcn` → **Public Subnet** → **Security List** (the default one).
2. **Add Ingress Rules** (two rules):
   - Source `0.0.0.0/0`, TCP, destination port **80**
   - Source `0.0.0.0/0`, TCP, destination port **443**
3. Port 22 (SSH) is already open. **Do not** add a rule for 5432 — the database must stay private.

### B3. VM 1 — the application
1. ☰ → **Compute → Instances → Create instance**.
2. Name: `legalcase-app`.
3. **Image:** Canonical **Ubuntu 24.04** (or 22.04). Click *Change image* if needed.
4. **Shape:** click *Change shape* → **Ampere** → **VM.Standard.A1.Flex** → **2 OCPU, 12 GB memory**.
5. **Networking:** select `legalcase-vcn` and the **public subnet**; tick **Assign a public IPv4 address**.
6. **SSH keys:** choose **Generate a key pair for me** → **Save private key** (keep it safe; you cannot download it again).
7. Boot volume: 50 GB is enough.
8. **Create**. Wait until the state is **Running**. Write down:
   - **Public IP** — e.g. `129.x.x.x`
   - **Private IP** — e.g. `10.0.0.x` (shown on the instance page)

### B4. VM 2 — the database
Repeat B3 with these changes:
- Name: `legalcase-db`
- **Subnet:** the **private subnet** (or the same public subnet, but **untick** *Assign a public IPv4 address*)
- Shape: **VM.Standard.A1.Flex**, **2 OCPU, 12 GB** (total with VM 1 = 4 OCPU / 24 GB, the Always Free maximum)
- Write down its **Private IP** (e.g. `10.0.0.y`).

> **"Out of host capacity"?** This is common for the ARM shape. Try another
> availability domain, or try again later in the day. As a fallback, VM 2 can be an
> **VM.Standard.E2.1.Micro** (1 GB RAM — enough for this demo database).

---

## Part C — Connect from Windows 11

Windows 11 has SSH built in (PowerShell). Put your private key file in a safe folder, e.g. `C:\Users\<you>\oracle\legalcase.key`.

```powershell
# 1. Connect to VM 1 (use the PUBLIC IP of legalcase-app)
ssh -i C:\Users\<you>\oracle\legalcase.key ubuntu@<VM1-public-ip>
```

First time: answer `yes` to the fingerprint question.
On Windows, if you get "permissions too open", run once:
`icacls C:\Users\<you>\oracle\legalcase.key /inheritance:r /grant:r "$env:USERNAME:R"`

To reach VM 2, first copy your key to VM 1 and hop from there:
```powershell
scp -i C:\Users\<you>\oracle\legalcase.key C:\Users\<you>\oracle\legalcase.key ubuntu@<VM1-public-ip>:~/.ssh/legalcase.key
# then, inside VM 1:  chmod 600 ~/.ssh/legalcase.key
#                     ssh -i ~/.ssh/legalcase.key ubuntu@<VM2-private-ip>
```

---

## Part D — Get the project onto the machines

**Option 1 — from GitHub (preferred):** push the project first (`push-to-github.bat`), then on each VM:
```bash
git clone https://github.com/<your-username>/legal-case-erp.git
cd legal-case-erp
```

**Option 2 — from your laptop (no GitHub):** in PowerShell on Windows, from the folder that contains the project zip:
```powershell
scp -i C:\Users\<you>\oracle\legalcase.key LegalCase-ERP-source-updated.zip ubuntu@<VM1-public-ip>:~/
ssh -i C:\Users\<you>\oracle\legalcase.key ubuntu@<VM1-public-ip> "sudo apt-get install -y unzip && unzip ~/LegalCase-ERP-source-updated.zip -d ~/legal-case-erp"
```

---

## Part E — Set up the database (VM 2) FIRST

SSH into **VM 2** (via VM 1, see Part C), then:
```bash
cd ~/legal-case-erp/deploy/oracle
bash setup-db-vm.sh <VM2-private-IP> <VM1-private-IP>
```
Example: `bash setup-db-vm.sh 10.0.0.12 10.0.0.7`

**Copy the password it prints.** It is shown once. (It is also saved in `.db.env` on VM 2, mode 600.)

---

## Part F — Set up the application (VM 1)

SSH into **VM 1** and run:
```bash
cd ~/legal-case-erp/deploy/oracle
bash setup-app-vm.sh
```

It asks three things:
1. **Domain name** — press Enter for now (plain HTTP by IP). See Part G for HTTPS.
2. **Private IP of VM 2** — e.g. `10.0.0.12`.
3. **Database password** — paste the one from Part E (typing is hidden).

It then installs Docker, builds the backend (5–10 minutes the first time), starts both containers, waits until healthy, and prints the address.

Open the address in your browser, e.g. `http://129.x.x.x`.
Sign in as `ally@mongalalaw.co.za` / `lawyer123`.

Check it from VM 1 at any time:
```bash
bash smoke-test.sh http://localhost:80
```
You should see **10 passed, 0 failed**.

> **Tip:** if you use a database password you typed yourself, use letters and digits only.

---

## Part G — HTTPS (optional, recommended for a public link)

Browsers warn about plain HTTP. For a real certificate you need a domain name:
1. Buy a cheap domain (e.g. `.co.za` or `.org`), or use a free subdomain service.
2. Create an **A record** pointing the domain at VM 1's **public IP**.
3. On VM 1: edit `.env` and set `SITE_ADDRESS=legalcase.yourdomain.co.za`, then:
   ```bash
   docker compose -f docker-compose.app.yml up -d
   ```
4. Caddy fetches the certificate automatically. Wait 1–2 minutes, then open `https://legalcase.yourdomain.co.za`.

Also make sure port **443** is open (Part B2) — it is, if you followed that step.

---

## Part H — Keep it running

| Task | Command (on the VM) |
|---|---|
| See logs | `docker compose -f docker-compose.app.yml logs -f backend` |
| Restart the app | `docker compose -f docker-compose.app.yml restart backend` |
| Update after a code change | `git pull` then `docker compose -f docker-compose.app.yml up -d --build` |
| Check the database | `docker exec -it legalcase-db psql -U legalcase -d legalcase -c "select count(*) from cases;"` |
| Back up the database (VM 2) | `docker exec legalcase-db pg_dump -U legalcase legalcase > backup-$(date +%F).sql` |

Machines restart automatically after a reboot (`restart: unless-stopped`), and firewall rules are saved.

---

## Evidence for the module (take these screenshots)

- Oracle console: both instances **Running**, with their **private IPs** visible.
- VM 1: `ip -4 addr` and `docker ps` (containers `legalcase-backend`, `legalcase-web`).
- VM 2: `ip -4 addr` and `docker ps` (container `legalcase-db`).
- VM 2: `docker exec legalcase-db psql -U legalcase -d legalcase -c "select count(*) from cases;"` — proves the data lives on the other machine.
- Browser: the public address, signed in.
- The smoke test output (10 passed).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Browser: "site cannot be reached" | Ingress rule missing in Oracle console | Part B2. Also check `sudo iptables -L INPUT -n --line-numbers` has ACCEPT for 80. |
| Health check fails in setup | Backend cannot reach the database | On VM 1: `docker logs legalcase-backend --tail 40`. Usually a wrong IP or password. Test: `nc -zv <VM2-private-IP> 5432`. |
| "password authentication failed" | Wrong database password typed in setup | Edit `LCE_DB_PASSWORD=` in `.env` on VM 1, then `docker compose -f docker-compose.app.yml up -d` |
| Setup on VM 2 says port already in use | Old container still running | `docker ps -a`, then `docker rm -f legalcase-db` and run setup again |
| Oracle "Out of host capacity" | ARM shape busy in your region | Try another availability domain or try later |
| Uploaded documents disappear | They are kept in the Docker volume `legalcase-data` on VM 1 and survive restarts. They are lost only if the volume is deleted. | Do not run `docker compose down -v` |

---

## Security notes (say these in the presentation)

- Only ports **80 and 443** are public. The database port 5432 is reachable only from VM 1, enforced by the private subnet, the host firewall (INPUT rule) **and** the Docker `DOCKER-USER` rule.
- Secrets (JWT key, document-encryption key, database password) are generated on the machine and never committed to GitHub. `.env` and `.db.env` are mode 600.
- Documents are encrypted at rest (AES-256-GCM) before they reach the disk.
- The seeded demo accounts have known passwords. For a public link, say it is a **demonstration deployment with synthetic data only** (POPIA: no real client data).
- Oracle's database link (`sslmode=disable`) stays on Oracle's private network. If you ever move VM 2 outside the VCN, enable TLS on PostgreSQL first.
