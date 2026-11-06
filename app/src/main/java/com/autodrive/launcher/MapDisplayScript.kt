package com.autodrive.launcher

import org.json.JSONObject

/** Replay only explicitly remembered display controls. No account/sharing/navigation actions. */
internal object MapDisplayScript {
    fun install(value: RememberedMapDisplay, canLocate: Boolean): String {
        val config = JSONObject().put("options", JSONObject(value.options.mapKeys { it.key.name }))
            .put("followOnStart", value.followOnStart).put("canLocate", canLocate).toString()
        return """
            (() => {
              if (window.top!==window || location.protocol!=='https:' ||
                  !['www.google.com','google.com','maps.google.com'].includes(location.hostname) ||
                  !(location.pathname==='/maps' || location.pathname.startsWith('/maps/'))) return false;
              if (window.__autoDriveMapDisplay) return true;
              const config=$config;
              const normalize=s=>(s||'').normalize('NFD').replace(/[\u0300-\u036f\u064b-\u065f\ue000-\uf8ff\u200e\u200f\u202a-\u202e]/g,'').replace(/\s+/g,' ').trim().toLowerCase();
              const names={
                TRAFFIC:['traffic','trafic','حركة المرور'],
                TRANSIT:['transit','public transport','public transit','transports en commun','lignes de transports en commun','النقل العام'],
                BICYCLING:['bicycling','cycling','biking','a velo','ركوب الدراجات'],
                SATELLITE:['satellite','القمر الصناعي','قمر صناعي','صور القمر الصناعي'],
                TERRAIN:['terrain','relief','التضاريس'],
                WILDFIRES:['wildfires','feux de foret','حرائق الغابات'],
                MOTION_CONTROLS:['map movement controls','commandes de mouvement de la carte','عناصر التحكم في حركة الخريطة']
              };
              const locationNames=['your location','show your location','recenter map on your location','re-center map on your location',
                'votre position','recentrer la carte sur votre position','موقعك الجغرافي','عرض موقعك الجغرافي'];
              const label=e=>normalize(e.getAttribute('aria-label')||e.getAttribute('title')||e.textContent);
              const controls=()=>[...document.querySelectorAll('button,[role="checkbox"],[role="menuitemcheckbox"],[role="switch"]')];
              const id=e=>Object.keys(names).find(key=>names[key].some(name=>normalize(name)===label(e)));
              const checked=e=>e.getAttribute('aria-checked')==='true'?true:e.getAttribute('aria-checked')==='false'?false:null;
              const usable=e=>!e.disabled && e.getAttribute('aria-disabled')!=='true' && checked(e)!==null;
              const state={userInteracted:false,lastGesture:0,followOnStart:config.followOnStart,restoring:true,attempted:{},locationAttempted:false};
              window.__autoDriveMapDisplay=state;
              const gesture=event=>{
                if(!event.isTrusted) return;
                state.userInteracted=true;state.lastGesture=Date.now();state.restoring=false;
                if(event.type==='click'){
                  const button=event.target.closest('button,[role="button"]');
                  if(button && locationNames.some(name=>normalize(name)===label(button))) state.followOnStart=true;
                }
              };
              ['click','touchend','pointerup','keydown'].forEach(type=>document.addEventListener(type,gesture,true));
              state.snapshot=()=>{
                if(!state.userInteracted || state.restoring || Date.now()-state.lastGesture<300) return null;
                const options={};const conflicts=new Set();
                controls().forEach(el=>{const key=id(el),value=checked(el);if(!key||value===null)return;
                  if(key in options && options[key]!==value) conflicts.add(key); else options[key]=value;});
                conflicts.forEach(key=>delete options[key]);
                return {schema:1,url:location.href,options,followOnStart:state.followOnStart};
              };
              const deadline=Date.now()+30000;
              const restore=()=>{
                if(!state.restoring || Date.now()>deadline) {clearInterval(state.timer);state.restoring=false;return;}
                const buttons=controls();
                Object.entries(config.options).forEach(([key,wanted])=>{
                  if(state.attempted[key]) return;
                  const button=buttons.find(el=>id(el)===key && usable(el));
                  if(!button) return;
                  state.attempted[key]=true;
                  if(checked(button)!==wanted) button.click();
                });
                if(config.followOnStart && config.canLocate && !state.locationAttempted){
                  const button=buttons.find(el=>locationNames.some(name=>normalize(name)===label(el)) && usable(el));
                  if(button){state.locationAttempted=true;if(checked(button)!==true)button.click();}
                }
                if(Object.keys(config.options).every(key=>state.attempted[key]) &&
                    (!config.followOnStart || !config.canLocate || state.locationAttempted)){
                  clearInterval(state.timer);state.restoring=false;
                }
              };
              state.timer=setInterval(restore,500);restore();return true;
            })();
        """.trimIndent()
    }
    val snapshot = "JSON.stringify(window.__autoDriveMapDisplay?.snapshot?.() || null)"
}
