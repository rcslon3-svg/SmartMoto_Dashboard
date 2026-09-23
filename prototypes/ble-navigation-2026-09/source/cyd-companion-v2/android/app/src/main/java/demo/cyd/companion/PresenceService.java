package demo.cyd.companion;
import android.companion.*;
public class PresenceService extends CompanionDeviceService {
 @Override public void onCreate(){super.onCreate();Journal.event(this,"CDM_SERVICE","CREATED","System bound companion service");}
 @Override public void onDeviceAppeared(AssociationInfo info){String a=info.getDeviceMacAddress()==null?"":info.getDeviceMacAddress().toString();if(a.equalsIgnoreCase(Config.address(this)))Monitoring.wake(this,"CDM_APPEARED","association="+info.getId()+"; API 33 callback means nearby OR connected");else Journal.event(this,"CDM","IGNORED_ASSOCIATION","id="+info.getId());}
 @Override public void onDeviceDisappeared(AssociationInfo info){Journal.event(this,"CDM_DISAPPEARED","CALLBACK","association="+info.getId()+"; not proof of physical power-off");}
 @Override public void onDestroy(){Journal.event(this,"CDM_SERVICE","DESTROYED","Service unbound; does not mean process died");super.onDestroy();}
}
