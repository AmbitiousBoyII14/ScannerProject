package com.example.live

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class TunnelService:VpnService(){
    private val worker=ScheduledThreadPoolExecutor(1)
    @Volatile private var stopping=false
    private var core:CoreController?=null
    private var ssh:SshTransport?=null
    private var tun:ParcelFileDescriptor?=null
    private var lastSample=0L
    private var lastHealth=0L
    private fun cleanup(){runCatching{core?.stopLoop()};core=null;runCatching{ssh?.close()};ssh=null;runCatching{tun?.close()};tun=null;LiveState.up=0.0;LiveState.down=0.0;LiveState.since=0;LiveState.ms=-1}
    private fun notice(msg:String):Notification {val nm=getSystemService(NotificationManager::class.java);val b=if(Build.VERSION.SDK_INT>=26){nm.createNotificationChannel(NotificationChannel("live","VPN connection",NotificationManager.IMPORTANCE_LOW));Notification.Builder(this,"live")}else Notification.Builder(this);val flags=PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT;return b.setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Scanner VPN").setContentText(msg).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,Intent(this,LiveActivity::class.java),flags)).addAction(android.R.drawable.ic_delete,"Disconnect",PendingIntent.getService(this,1,Intent(this,TunnelService::class.java).setAction("stop"),flags)).build()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="stop"){stopTunnel();return START_NOT_STICKY};if(intent?.action!="connect"||LiveState.status!="Starting"){if(core==null)stopSelf();return START_NOT_STICKY}
        startForeground(73,notice("Starting tunnel…"));val p=LiveState.active()?:run{stopTunnel();return START_NOT_STICKY}
        worker.execute{try{
            Engine.init(this);val ip=LiveState.resolve(p);ssh=p.openSsh(ip){t,m->LiveState.log(t,m)};ssh?.report{tag,msg->LiveState.log(tag,msg)};val cfg=p.config(ip,ssh);if(stopping)return@execute
            LiveState.log("VPN","Installing default IPv4 route · DNS ${p.dns}");tun=Builder().setSession("Scanner VPN").setMtu(1500).addAddress("10.77.0.2",30).addRoute("0.0.0.0",0).addDnsServer(p.dns).addDisallowedApplication(packageName).establish()?:error("Permission revoked")
            val engine=Libv2ray.newCoreController(object:CoreCallbackHandler{override fun startup()=0L;override fun shutdown()=0L;override fun onEmitStatus(code:Long,msg:String?)=0L});core=engine
            engine.startLoop(cfg,tun!!.fd);LiveState.log("TUN","Android interface active; verifying HTTPS through ${p.host}:${p.port}");if(stopping)return@execute
            LiveState.ms=engine.measureDelay(CHECK_URL).also{check(it>=0)};if(stopping)return@execute
            LiveState.status="Connected";LiveState.since=SystemClock.elapsedRealtime();lastSample=LiveState.since;lastHealth=lastSample
            LiveState.log("ONLINE","Verified ${p.label} · ${LiveState.ms} ms");getSystemService(NotificationManager::class.java).notify(73,notice("Connected · ${p.host}"));worker.scheduleWithFixedDelay({sample()},1,1,TimeUnit.SECONDS)
        }catch(e:Exception){if(!stopping)fail(e)}};return START_NOT_STICKY
    }
    private fun sample(){if(stopping)return;val engine=core?:return;try{val now=SystemClock.elapsedRealtime();val scale=1.0/(now-lastSample).coerceAtLeast(1);lastSample=now;var up=0.0;var down=0.0;engine.queryAllOutboundTrafficStats().split(';').forEach{val f=it.split(',');if(f.size==3&&f[0]=="vpn"){val v=(f[2].toLongOrNull()?:0)*scale;if(f[1]=="uplink")up+=v else if(f[1]=="downlink")down+=v}};LiveState.up=up;LiveState.down=down;if(now-lastHealth>=60000){lastHealth=now;LiveState.ms=engine.measureDelay(CHECK_URL).also{check(it>=0)}}}catch(e:Exception){if(!stopping)fail(e)}}
    private fun fail(e:Exception){stopping=true;LiveState.log("ERROR",failureReason(e,"Could not verify HTTPS through this profile. Confirm the server supports the selected protocol and the check site is reachable."));cleanup();LiveState.status="Failed";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    private fun stopTunnel(){if(stopping)return;stopping=true;LiveState.status="Stopping";worker.execute{cleanup();LiveState.log("STOP","Tunnel closed");LiveState.status="Idle";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
    override fun onRevoke(){stopTunnel()}
    override fun onDestroy(){stopping=true;worker.execute{cleanup();if(LiveState.status!="Failed")LiveState.status="Idle"};worker.shutdown();super.onDestroy()}
}
