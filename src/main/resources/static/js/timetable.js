/* ============================================================
   MANAS UNIVERSITY — SMART TIMETABLE
   timetable.js  — plain ES5, no JSX, no Babel needed
   ============================================================ */

var e = React.createElement;
var useState  = React.useState;
var useEffect = React.useEffect;
var useMemo   = React.useMemo;
var useRef    = React.useRef;

/* ── UI labels only — NO hardcoded data ──────────────────── */
var LABELS = {
    EN:{
        subtitle:"Smart Timetable",
        tabs:{student:"Student / Group", professor:"Professor", room:"Room"},
        search:{
            student:"Type group name (e.g. CS-101, MYO-135)…",
            professor:"Type lecturer name…",
            room:"Type room or building…"
        },
        filter:{
            student:"Filter by Faculty",   all_student:"All Faculties",
            professor:"Filter by Faculty", all_professor:"All Faculties",
            room:"Filter by Building",     all_room:"All Buildings",
        },
        semesters:["Spring 2025","Fall 2024","Spring 2024","Fall 2023"],
        all_sem:"All Semesters",
        days:["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"],
        daysShort:["Mon","Tue","Wed","Thu","Fri","Sat"],
        types:{lecture:"Lecture",seminar:"Seminar",lab:"Lab"},
        live:"LIVE", noClass:"Free", period:"Period",
        noSearch:"Enter a group, lecturer, or room name to view the timetable.",
        noResults:"No classes found.", refresh:"Refresh", updated:"Updated",
    },
    RU:{
        subtitle:"Умное Расписание",
        tabs:{student:"Студент / Группа", professor:"Преподаватель", room:"Аудитория"},
        search:{
            student:"Введите группу (напр. CS-101, МЮО-135)…",
            professor:"Введите имя преподавателя…",
            room:"Введите аудиторию или корпус…"
        },
        filter:{
            student:"Фильтр по факультету",   all_student:"Все факультеты",
            professor:"Фильтр по факультету", all_professor:"Все факультеты",
            room:"Фильтр по корпусу",         all_room:"Все корпуса",
        },
        semesters:["Весна 2025","Осень 2024","Весна 2024","Осень 2023"],
        all_sem:"Все семестры",
        days:["Понедельник","Вторник","Среда","Четверг","Пятница","Суббота"],
        daysShort:["Пн","Вт","Ср","Чт","Пт","Сб"],
        types:{lecture:"Лекция",seminar:"Семинар",lab:"Лаб."},
        live:"ЭФ.", noClass:"Свободно", period:"Пара",
        noSearch:"Введите группу, преподавателя или аудиторию для просмотра расписания.",
        noResults:"Занятия не найдены.", refresh:"Обновить", updated:"Обновлено",
    },
    TR:{
        subtitle:"Akıllı Ders Programı",
        tabs:{student:"Öğrenci / Grup", professor:"Öğretim Üyesi", room:"Derslik"},
        search:{
            student:"Grup adı girin (örn. CS-101)…",
            professor:"Öğretim üyesi adı girin…",
            room:"Derslik veya bina girin…"
        },
        filter:{
            student:"Fakülteye göre filtrele",   all_student:"Tüm Fakülteler",
            professor:"Fakülteye göre filtrele", all_professor:"Tüm Fakülteler",
            room:"Binaya göre filtrele",         all_room:"Tüm Binalar",
        },
        semesters:["Bahar 2025","Güz 2024","Bahar 2024","Güz 2023"],
        all_sem:"Tüm Sömestrler",
        days:["Pazartesi","Salı","Çarşamba","Perşembe","Cuma","Cumartesi"],
        daysShort:["Pzt","Sal","Çar","Per","Cum","Cmt"],
        types:{lecture:"Ders",seminar:"Seminer",lab:"Lab"},
        live:"CANLI", noClass:"Boş", period:"Ders",
        noSearch:"Grup, öğretim üyesi veya derslik girerek programı görüntüleyin.",
        noResults:"Ders bulunamadı.", refresh:"Yenile", updated:"Güncellendi",
    },
    KG:{
        subtitle:"Акылдуу Сабак Графиги",
        tabs:{student:"Студент / Топ", professor:"Окутуучу", room:"Аудитория"},
        search:{
            student:"Топтун атын жазыңыз…",
            professor:"Окутуучунун атын жазыңыз…",
            room:"Аудитория же корпусту жазыңыз…"
        },
        filter:{
            student:"Факультет боюнча",   all_student:"Бардык факультеттер",
            professor:"Факультет боюнча", all_professor:"Бардык факультеттер",
            room:"Корпус боюнча",         all_room:"Бардык корпустар",
        },
        semesters:["Жаз 2025","Күз 2024","Жаз 2024","Күз 2023"],
        all_sem:"Бардык семестрлер",
        days:["Дүйшөмбү","Шейшемби","Шаршемби","Бейшемби","Жума","Ишемби"],
        daysShort:["Дүй","Шей","Шар","Бей","Жум","Иш"],
        types:{lecture:"Лекция",seminar:"Семинар",lab:"Лаб."},
        live:"ТИКЕ", noClass:"Бош", period:"Пара",
        noSearch:"Топ, окутуучу же аудиторияны киргизип, графикти кароңуз.",
        noResults:"Сабак табылган жок.", refresh:"Жаңылоо", updated:"Жаңыланды",
    },
};

var SLOTS = [
    {id:0,start:"08:00",end:"09:30",sh:8, sm:0, eh:9, em:30},
    {id:1,start:"09:40",end:"11:10",sh:9, sm:40,eh:11,em:10},
    {id:2,start:"11:20",end:"12:50",sh:11,sm:20,eh:12,em:50},
    {id:3,start:"13:40",end:"15:10",sh:13,sm:40,eh:15,em:10},
    {id:4,start:"15:20",end:"16:50",sh:15,sm:20,eh:16,em:50},
    {id:5,start:"17:00",end:"18:30",sh:17,sm:0, eh:18,em:30},
];

var TYPE_STYLE = {
    lecture:{bg:"#EEF2FF",border:"#818CF8",text:"#4338CA",tag:"rgba(99,102,241,.12)"},
    seminar:{bg:"#ECFDF5",border:"#34D399",text:"#065F46",tag:"rgba(16,185,129,.12)"},
    lab:    {bg:"#FFF7ED",border:"#FB923C",text:"#9A3412",tag:"rgba(249,115,22,.12)"},
};

/* ── Parse server-injected JSON ──────────────────────────── */
function parseJ(raw, fb) {
    try {
        if (typeof raw === 'string' && raw.length > 2) return JSON.parse(raw);
        if (Array.isArray(raw)) return raw;
    } catch(x) { console.warn('[TT] parse error', x); }
    return fb || [];
}

var INIT_LESSONS    = parseJ(window.__TIMETABLE_DATA__,  []).map(function(l,i){
    return {
        id:        Number(l.id) || i+1,
        group:     String(l.group    ||'UNKNOWN').trim(),
        day:       Number(l.day)     || 0,
        slot:      Number(l.slot)    || 0,
        subject:   String(l.subject  ||'Lesson').trim(),
        type:      String(l.type     ||'lecture').trim(),
        professor: String(l.professor||'').trim(),
        room:      String(l.room     ||'').trim(),
        faculty:   String(l.faculty  ||'').trim(),
    };
});
var INIT_FACULTIES  = parseJ(window.__FACULTIES_DATA__,  []);
var INIT_GROUPS     = parseJ(window.__GROUPS_DATA__,     []);
var INIT_PROFESSORS = parseJ(window.__PROFESSORS_DATA__, []);
var INIT_ROOMS      = parseJ(window.__ROOMS_DATA__,      []);

/* ── Icons ───────────────────────────────────────────────── */
function IcoSearch()   { return e('svg',{width:15,height:15,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.5",strokeLinecap:"round",strokeLinejoin:"round"},e('circle',{cx:"11",cy:"11",r:"8"}),e('line',{x1:"21",y1:"21",x2:"16.65",y2:"16.65"})); }
function IcoUsers()    { return e('svg',{width:14,height:14,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('path',{d:"M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"}),e('circle',{cx:"9",cy:"7",r:"4"}),e('path',{d:"M23 21v-2a4 4 0 00-3-3.87"}),e('path',{d:"M16 3.13a4 4 0 010 7.75"})); }
function IcoUser(p)    { var s=(p&&p.size)||14; return e('svg',{width:s,height:s,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('path',{d:"M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"}),e('circle',{cx:"12",cy:"7",r:"4"})); }
function IcoDoor()     { return e('svg',{width:14,height:14,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('path',{d:"M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"}),e('polyline',{points:"9,22 9,12 15,12 15,22"})); }
function IcoGlobe()    { return e('svg',{width:14,height:14,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('circle',{cx:"12",cy:"12",r:"10"}),e('line',{x1:"2",y1:"12",x2:"22",y2:"12"}),e('path',{d:"M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z"})); }
function IcoPin(p)     { var s=(p&&p.size)||11; return e('svg',{width:s,height:s,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('path',{d:"M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0116 0Z"}),e('circle',{cx:"12",cy:"10",r:"3"})); }
function IcoFlask(p)   { var s=(p&&p.size)||11; return e('svg',{width:s,height:s,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('path',{d:"M9 3h6l1 11H8L9 3z"}),e('path',{d:"M6.5 14.5c-1.5 1-2.5 2-2.5 3.5a2.5 2.5 0 002.5 2.5h11A2.5 2.5 0 0020 18c0-1.5-1-2.5-2.5-3.5"})); }
function IcoCalendar(p){ var s=(p&&p.size)||14; return e('svg',{width:s,height:s,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('rect',{x:"3",y:"4",width:"18",height:"18",rx:"2",ry:"2"}),e('line',{x1:"16",y1:"2",x2:"16",y2:"6"}),e('line',{x1:"8",y1:"2",x2:"8",y2:"6"}),e('line',{x1:"3",y1:"10",x2:"21",y2:"10"})); }
function IcoChevron(p) { var d=(p&&p.dir)||"down"; return e('svg',{width:13,height:13,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2.5",strokeLinecap:"round",strokeLinejoin:"round"},e('polyline',{points:d==="up"?"18 15 12 9 6 15":"6 9 12 15 18 9"})); }
function IcoRefresh()  { return e('svg',{width:13,height:13,viewBox:"0 0 24 24",fill:"none",stroke:"currentColor",strokeWidth:"2",strokeLinecap:"round",strokeLinejoin:"round"},e('polyline',{points:"23 4 23 10 17 10"}),e('path',{d:"M20.49 15a9 9 0 11-2.12-9.36L23 10"})); }

/* ── LessonCard ──────────────────────────────────────────── */
function LessonCard(p) {
    var l=p.lesson, t=p.t, live=p.isLive;
    var s=TYPE_STYLE[l.type]||TYPE_STYLE.lecture;
    return e('div',{className:"tt-lesson-card tt-fade-up",style:{background:live?"#FFFBEB":s.bg,borderLeft:"3px solid "+(live?"#F59E0B":s.border),borderRadius:"9px",padding:"9px 11px",height:"100%",minHeight:"90px",display:"flex",flexDirection:"column",justifyContent:"space-between",boxShadow:live?"0 4px 16px rgba(245,158,11,.18)":"0 1px 4px rgba(0,0,0,.06)",position:"relative",overflow:"hidden"}},
        live&&e('div',{className:"tt-live-glow",style:{position:"absolute",top:7,right:7,background:"#F59E0B",color:"white",fontSize:"8.5px",fontWeight:"700",letterSpacing:".08em",padding:"2px 7px",borderRadius:"20px",display:"flex",alignItems:"center",gap:"4px"}},
            e('span',{className:"tt-live-dot",style:{width:5,height:5,background:"white",borderRadius:"50%",display:"inline-block"}}),t.live),
        e('div',{style:{fontSize:"11.5px",fontWeight:"600",color:"#1C1C2E",lineHeight:"1.35",paddingRight:live?"46px":"0",marginBottom:"6px"}},l.subject),
        e('div',{style:{marginBottom:"5px"}},
            e('span',{style:{display:"inline-flex",alignItems:"center",gap:"3px",fontSize:"9px",fontWeight:"700",textTransform:"uppercase",letterSpacing:".07em",color:s.text,background:s.tag,padding:"2px 7px",borderRadius:"4px"}},
                l.type==="lab"?e(IcoFlask,{}):null, t.types[l.type]||l.type)),
        e('div',{style:{display:"flex",flexDirection:"column",gap:"2px"}},
            l.professor&&e('div',{style:{display:"flex",alignItems:"center",gap:"4px"}},
                e('span',{style:{color:"#9CA3AF"}},e(IcoUser,{size:10})),
                e('span',{style:{fontSize:"10.5px",color:"#4B5563",fontWeight:"500",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}},l.professor)),
            l.room&&e('div',{style:{display:"flex",alignItems:"center",gap:"4px"}},
                e('span',{style:{color:"#9CA3AF"}},e(IcoPin,{})),
                e('span',{style:{fontSize:"10.5px",color:"#6B7280"}},l.room)),
            l.faculty&&e('div',{style:{display:"flex",alignItems:"center",gap:"4px"}},
                e('span',{style:{fontSize:"9.5px",color:"#9CA3AF",fontStyle:"italic",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}},l.faculty))
        )
    );
}

function EmptySlot(p) {
    return e('div',{style:{height:"100%",minHeight:"90px",border:"1.5px dashed #DDD9D0",borderRadius:"9px",display:"flex",alignItems:"center",justifyContent:"center"}},
        e('span',{style:{fontSize:"10px",color:"#C9C5BC",fontWeight:"500"}},p.label));
}

/* ── Autocomplete dropdown ───────────────────────────────── */
function Dropdown(p) {
    if (!p.visible||!p.items||!p.items.length) return null;
    return e('div',{className:"tt-drop-in",style:{position:"absolute",top:"calc(100% + 4px)",left:0,right:0,background:"white",borderRadius:"10px",border:"1.5px solid #E8E4DC",boxShadow:"0 8px 24px rgba(0,0,0,.1)",zIndex:9999,overflow:"hidden",maxHeight:"240px",overflowY:"auto"}},
        p.items.map(function(item,i){
            return e('button',{key:i,onMouseDown:function(){p.onSelect(item);},
                style:{display:"block",width:"100%",textAlign:"left",padding:"9px 14px",background:"none",border:"none",fontFamily:"'DM Sans',sans-serif",fontSize:"13px",color:"#1C1C2E",cursor:"pointer",borderBottom:i<p.items.length-1?"1px solid #F5F3EE":"none"},
                onMouseEnter:function(ev){ev.currentTarget.style.background="#F5F3EE";},
                onMouseLeave:function(ev){ev.currentTarget.style.background="none";}},item);
        }));
}

/* ── Desktop Grid ────────────────────────────────────────── */
function DesktopGrid(p) {
    var lessons=p.lessons,t=p.t,cs=p.currentSlot,cd=p.currentDay,has=p.hasSearch;
    var map=useMemo(function(){var m={};lessons.forEach(function(l){m[l.day+"-"+l.slot]=l;});return m;},[lessons]);
    return e('div',{style:{background:"white",borderRadius:18,boxShadow:"0 2px 16px rgba(0,0,0,.07)",overflow:"hidden",border:"1px solid var(--border)"}},
        /* Day headers */
        e('div',{className:"tt-sched-grid",style:{borderBottom:"1px solid #F5F3EE"}},
            e('div',{style:{padding:"14px 10px",borderRight:"1px solid #F5F3EE",display:"flex",alignItems:"center",justifyContent:"center"}},e(IcoCalendar,{size:16})),
            t.days.map(function(day,i){
                return e('div',{key:i,style:{padding:"14px 8px",textAlign:"center",fontSize:"12px",fontWeight:"700",color:i===cd?"var(--crimson)":"#374151",borderRight:i<5?"1px solid #F5F3EE":"none",background:i===cd?"rgba(139,0,0,.03)":"transparent",letterSpacing:".04em",textTransform:"uppercase"}},
                    t.daysShort[i],i===cd&&e('div',{style:{width:4,height:4,borderRadius:"50%",background:"var(--crimson)",margin:"4px auto 0"}}));
            })),
        /* Slot rows */
        SLOTS.map(function(slot,si){
            var isCur=si===cs;
            return e('div',{key:si,className:"tt-sched-grid"+(isCur?" tt-row-live":""),style:{borderBottom:si<5?"1px solid #F5F3EE":"none",minHeight:110}},
                e('div',{style:{padding:"10px 8px",borderRight:"1px solid #F5F3EE",display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",gap:2}},
                    isCur&&e('span',{style:{display:"inline-flex",alignItems:"center",gap:3,fontSize:"7.5px",fontWeight:"700",color:"var(--crimson)",background:"rgba(139,0,0,.07)",padding:"1px 6px",borderRadius:"20px",letterSpacing:".08em",marginBottom:2}},
                        e('span',{className:"tt-live-dot",style:{width:4,height:4,background:"var(--crimson)",borderRadius:"50%",display:"inline-block"}}),t.live),
                    e('span',{style:{fontSize:"10px",fontWeight:"700",color:isCur?"var(--crimson)":"#374151"}},t.period+" "+(si+1)),
                    e('span',{style:{fontSize:"9.5px",color:"#9CA3AF",fontWeight:"500",whiteSpace:"nowrap"}},slot.start),
                    e('span',{style:{fontSize:"9px",color:"#C9C5BC"}},"—"),
                    e('span',{style:{fontSize:"9.5px",color:"#9CA3AF",fontWeight:"500",whiteSpace:"nowrap"}},slot.end)),
                [0,1,2,3,4,5].map(function(di){
                    var lesson=map[di+"-"+si], live=isCur&&di===cd;
                    return e('div',{key:di,style:{padding:"8px 6px",borderRight:di<5?"1px solid #F5F3EE":"none"}},
                        has&&lesson?e(LessonCard,{lesson:lesson,t:t,isLive:live}):has?e(EmptySlot,{label:t.noClass}):null);
                }));
        }));
}

/* ── Mobile View ─────────────────────────────────────────── */
function MobileView(p) {
    var lessons=p.lessons,t=p.t,cs=p.currentSlot,cd=p.currentDay,has=p.hasSearch;
    var s=useState(cd>=0?cd:0); var ad=s[0],setAd=s[1];
    var dl=useMemo(function(){return lessons.filter(function(l){return l.day===ad;});},[lessons,ad]);
    return e('div',{},
        e('div',{style:{display:"flex",overflowX:"auto",gap:8,padding:"0 0 12px",scrollbarWidth:"none"}},
            t.daysShort.map(function(d,i){
                return e('button',{key:i,onClick:function(){setAd(i);},style:{flexShrink:0,padding:"8px 14px",borderRadius:10,border:"none",cursor:"pointer",fontSize:12,fontWeight:700,background:i===ad?"var(--navy)":"#F5F3EE",color:i===ad?"white":"#6B7280",boxShadow:i===ad?"0 4px 14px rgba(0,35,102,.28)":"none",position:"relative"}},
                    d,i===cd&&e('span',{style:{position:"absolute",top:4,right:4,width:5,height:5,borderRadius:"50%",background:i===ad?"#fff":"var(--crimson)"}}));
            })),
        has&&SLOTS.map(function(slot,si){
            var lesson=dl.find(function(l){return l.slot===si;}), live=si===cs&&ad===cd;
            return e('div',{key:si,style:{marginBottom:8,display:"flex",gap:10,alignItems:"stretch"}},
                e('div',{style:{width:56,flexShrink:0,display:"flex",flexDirection:"column",alignItems:"center",justifyContent:"center",background:"white",borderRadius:10,padding:"8px 4px",border:"1px solid var(--border)"}},
                    e('span',{style:{fontSize:"9px",fontWeight:"700",color:live?"var(--crimson)":"#374151"}},t.period+(si+1)),
                    e('span',{style:{fontSize:"9px",color:"#9CA3AF",marginTop:2}},slot.start)),
                e('div',{style:{flex:1}},lesson?e(LessonCard,{lesson:lesson,t:t,isLive:live}):e(EmptySlot,{label:t.noClass})));
        }));
}

/* ── Live refresh ────────────────────────────────────────── */
function useLive() {
    var s1=useState(INIT_LESSONS);    var data=s1[0],setData=s1[1];
    var s2=useState(false);           var loading=s2[0],setLoading=s2[1];
    var s3=useState(null);            var ts=s3[0],setTs=s3[1];
    var s4=useState(INIT_FACULTIES);  var faculties=s4[0],setFaculties=s4[1];
    var s5=useState(INIT_GROUPS);     var groups=s5[0],setGroups=s5[1];
    var s6=useState(INIT_PROFESSORS); var professors=s6[0],setProfessors=s6[1];
    var s7=useState(INIT_ROOMS);      var rooms=s7[0],setRooms=s7[1];

    function refresh() {
        setLoading(true);
        fetch('/timetable/api/all',{credentials:'same-origin'})
            .then(function(r){return r.ok?r.json():null;})
            .then(function(json){
                if(json&&json.length){
                    var parsed=json.map(function(l,i){return{id:Number(l.id)||i+1,group:String(l.group||'').trim(),day:Number(l.day)||0,slot:Number(l.slot)||0,subject:String(l.subject||'').trim(),type:String(l.type||'lecture').trim(),professor:String(l.professor||'').trim(),room:String(l.room||'').trim(),faculty:String(l.faculty||'').trim()};});
                    setData(parsed);
                    setFaculties([...new Set(parsed.map(function(l){return l.faculty;}).filter(Boolean))].sort());
                    setGroups([...new Set(parsed.map(function(l){return l.group;}).filter(Boolean))].sort());
                    setProfessors([...new Set(parsed.map(function(l){return l.professor;}).filter(Boolean))].sort());
                    setRooms([...new Set(parsed.map(function(l){return l.room;}).filter(Boolean))].sort());
                    setTs(new Date().toLocaleTimeString());
                }
                setLoading(false);
            }).catch(function(){setLoading(false);});
    }
    useEffect(function(){var id=setInterval(refresh,10*60*1000);return function(){clearInterval(id);};},[]);
    return {data:data,loading:loading,ts:ts,faculties:faculties,groups:groups,professors:professors,rooms:rooms,refresh:refresh};
}

/* ── Main App ────────────────────────────────────────────── */
function TimetableApp() {
    /* Lang */
    var ls=useState(function(){
        var p=new URLSearchParams(window.location.search),lp=(p.get('lang')||'').toUpperCase();
        if(lp==='KY'||lp==='KG')return'KG';
        if(['EN','RU','TR','KG'].includes(lp))return lp;
        var loc=(document.documentElement.lang||'en').toUpperCase();
        if(loc==='TR')return'TR'; if(loc==='RU')return'RU'; if(loc==='KY')return'KG';
        return'EN';
    });
    var lang=ls[0],setLang=ls[1];
    var t=LABELS[lang]||LABELS.EN;

    /* State */
    var sv=useState("student"); var view=sv[0],setView=sv[1];
    var ss=useState("");        var search=ss[0],setSearch=ss[1];
    var sf=useState("");        var filterVal=sf[0],setFilterVal=sf[1];
    var sm=useState("");        var semester=sm[0],setSemester=sm[1];
    var sg=useState(false);     var showSugg=sg[0],setShowSugg=sg[1];
    var sl=useState(false);     var langOpen=sl[0],setLangOpen=sl[1];
    var mob=useState(window.innerWidth<768); var isMobile=mob[0],setIsMobile=mob[1];
    var sc=useState(-1);        var currentSlot=sc[0],setCurrentSlot=sc[1];
    var sd=useState(-1);        var currentDay=sd[0],setCurrentDay=sd[1];
    var searchRef=useRef(null), langRef=useRef(null);

    var live=useLive();

    /* Responsive */
    useEffect(function(){
        function r(){setIsMobile(window.innerWidth<768);}
        window.addEventListener('resize',r); return function(){window.removeEventListener('resize',r);};
    },[]);

    /* Live clock */
    useEffect(function(){
        function tick(){
            var now=new Date(), day=now.getDay()-1;
            setCurrentDay(day>=0&&day<=5?day:-1);
            var mins=now.getHours()*60+now.getMinutes(), found=-1;
            SLOTS.forEach(function(s,i){if(mins>=s.sh*60+s.sm&&mins<=s.eh*60+s.em)found=i;});
            setCurrentSlot(found);
        }
        tick(); var id=setInterval(tick,30000); return function(){clearInterval(id);};
    },[]);

    /* Close lang on outside click */
    useEffect(function(){
        function h(ev){if(langRef.current&&!langRef.current.contains(ev.target))setLangOpen(false);}
        document.addEventListener("mousedown",h); return function(){document.removeEventListener("mousedown",h);};
    },[]);

    function switchView(v){setView(v);setSearch("");setFilterVal("");setShowSugg(false);}

    /* ── Context-aware second dropdown items ──
       Student tab → show faculties
       Professor tab → show professors list
       Room tab → show rooms list
    */
    var secondDropdownItems = useMemo(function(){
        if(view==="student")   return live.faculties;
        if(view==="professor") return live.professors;
        if(view==="room")      return live.rooms;
        return [];
    },[view, live.faculties, live.professors, live.rooms]);

    var secondDropdownLabel = t.filter["all_"+view] || "All";
    var secondDropdownTitle = t.filter[view] || "";

    /* ── Search autocomplete suggestions ── */
    var suggestions = useMemo(function(){
        if(!search.trim()) return [];
        var q=search.toLowerCase();
        var pool;
        if(view==="student")   pool=live.groups;
        else if(view==="professor") pool=live.professors;
        else pool=live.rooms;
        return pool.filter(function(x){return x.toLowerCase().includes(q);}).slice(0,10);
    },[search,view,live.groups,live.professors,live.rooms]);

    var hasSearch = search.trim().length>0;

    /* ── Filter lessons ── */
    var filtered = useMemo(function(){
        if(!hasSearch) return [];
        var q=search.trim().toLowerCase();
        return live.data.filter(function(l){
            if(view==="student"   &&!(l.group    ||'').toLowerCase().includes(q)) return false;
            if(view==="professor" &&!(l.professor||'').toLowerCase().includes(q)) return false;
            if(view==="room"      &&!(l.room     ||'').toLowerCase().includes(q)) return false;
            /* second dropdown filter — context aware */
            if(filterVal){
                if(view==="student"   && l.faculty   && l.faculty   !==filterVal) return false;
                if(view==="professor" && l.professor && l.professor !==filterVal) return false;
                if(view==="room"      && l.room      && l.room      !==filterVal) return false;
            }
            return true;
        });
    },[search,view,filterVal,hasSearch,live.data]);

    /* ── Render ── */
    return e('div',{style:{minHeight:"calc(100vh - 64px)",background:"#F2F0EB",position:"relative"}},

        /* Sub-header */
        e('header',{style:{background:"linear-gradient(160deg,#001A50 0%,#002366 55%,#1C006A 100%)",boxShadow:"0 2px 20px rgba(0,0,0,.25)",padding:isMobile?"14px 16px":"14px 28px",display:"flex",alignItems:"center",justifyContent:"space-between",flexWrap:"wrap",gap:10,position:"sticky",top:0,zIndex:200}},
            e('div',{style:{display:"flex",alignItems:"center",gap:14}},
                e('div',{style:{display:"flex",flexDirection:"column"}},
                    e('span',{style:{fontFamily:"'Cormorant Garamond',serif",fontSize:isMobile?17:20,fontWeight:600,color:"white",lineHeight:1.1}},"Manas"),
                    e('span',{style:{fontSize:isMobile?9:10,fontWeight:700,color:"rgba(201,164,74,.9)",letterSpacing:".14em",textTransform:"uppercase"}},t.subtitle)),
                live.ts&&e('span',{style:{fontSize:10,color:"rgba(255,255,255,.4)",marginLeft:8}},t.updated+": "+live.ts)),
            e('div',{style:{display:"flex",alignItems:"center",gap:8}},
                e('button',{onClick:live.refresh,style:{display:"flex",alignItems:"center",gap:5,background:"rgba(255,255,255,.1)",border:"1px solid rgba(255,255,255,.18)",borderRadius:8,padding:"7px 10px",cursor:"pointer",color:"white",fontSize:11,fontWeight:600}},
                    e(IcoRefresh,{})," "+(live.loading?"…":t.refresh)),
                e('div',{ref:langRef,style:{position:"relative"}},
                    e('button',{onClick:function(){setLangOpen(!langOpen);},style:{display:"flex",alignItems:"center",gap:6,background:"rgba(255,255,255,.1)",border:"1px solid rgba(255,255,255,.18)",borderRadius:8,padding:"7px 12px",cursor:"pointer",color:"white",fontSize:12,fontWeight:700}},
                        e(IcoGlobe,{})," "+lang+" ",e(IcoChevron,{dir:langOpen?"up":"down"})),
                    langOpen&&e('div',{className:"tt-drop-in",style:{position:"absolute",top:"calc(100% + 6px)",right:0,background:"white",borderRadius:10,border:"1.5px solid #E8E4DC",boxShadow:"0 8px 24px rgba(0,0,0,.12)",zIndex:9999,overflow:"hidden",minWidth:80}},
                        Object.keys(LABELS).map(function(lk){
                            return e('button',{key:lk,onClick:function(){setLang(lk);setLangOpen(false);},style:{display:"block",width:"100%",textAlign:"left",padding:"9px 16px",background:lk===lang?"#F5F3EE":"none",border:"none",fontSize:13,fontWeight:lk===lang?700:500,color:lk===lang?"var(--navy)":"#374151",cursor:"pointer"}},lk);
                        }))))),

        /* Main */
        e('main',{style:{maxWidth:1440,margin:"0 auto",padding:isMobile?"16px 14px 48px":"24px 28px 60px",position:"relative",zIndex:1}},

            /* Control panel */
            e('div',{style:{background:"white",borderRadius:18,boxShadow:"0 2px 16px rgba(0,0,0,.07)",padding:isMobile?"18px 16px":"22px 26px",marginBottom:18,border:"1px solid var(--border)"}},

                /* View tabs */
                e('div',{style:{display:"flex",alignItems:"center",gap:6,marginBottom:18,flexWrap:"wrap"}},
                    [{id:"student",icon:e(IcoUsers,{}),key:"student"},
                        {id:"professor",icon:e(IcoUser,{}),key:"professor"},
                        {id:"room",icon:e(IcoDoor,{}),key:"room"}].map(function(tab){
                        var active=view===tab.id;
                        return e('button',{key:tab.id,onClick:function(){switchView(tab.id);},
                                style:{display:"flex",alignItems:"center",gap:8,padding:"9px 18px",borderRadius:10,border:"none",cursor:"pointer",fontSize:13.5,fontWeight:600,transition:"all .18s",background:active?"var(--navy)":"#F5F3EE",color:active?"white":"#6B7280",boxShadow:active?"0 4px 14px rgba(0,35,102,.28)":"none"}},
                            tab.icon," "+t.tabs[tab.key]);
                    })),

                /* Search row */
                e('div',{style:{display:"flex",gap:10,flexWrap:"wrap",alignItems:"flex-start"}},

                    /* Search input */
                    e('div',{style:{position:"relative",flex:"1",minWidth:220},ref:searchRef},
                        e('div',{style:{position:"absolute",left:11,top:"50%",transform:"translateY(-50%)",color:"#9CA3AF",pointerEvents:"none"}},e(IcoSearch,{})),
                        e('input',{type:"text",className:"tt-form-input",value:search,
                            onChange:function(ev){setSearch(ev.target.value);setShowSugg(true);},
                            onFocus:function(){setShowSugg(true);},
                            onBlur:function(){setTimeout(function(){setShowSugg(false);},180);},
                            placeholder:t.search[view],
                            style:{paddingLeft:35}}),
                        e(Dropdown,{items:suggestions,onSelect:function(v){setSearch(v);setShowSugg(false);},visible:showSugg&&suggestions.length>0})),

                    /* Context-aware second dropdown */
                    e('div',{style:{position:"relative",minWidth:190}},
                        e('select',{className:"tt-form-input",value:filterVal,
                                onChange:function(ev){setFilterVal(ev.target.value);},
                                style:{paddingRight:30},
                                title:secondDropdownTitle},
                            e('option',{value:""},secondDropdownLabel),
                            secondDropdownItems.map(function(item){
                                return e('option',{key:item,value:item},item);
                            })),
                        e('div',{style:{position:"absolute",right:9,top:"50%",transform:"translateY(-50%)",pointerEvents:"none",color:"#9CA3AF"}},e(IcoChevron,{}))),

                    /* Semester */
                    e('div',{style:{position:"relative",minWidth:140}},
                        e('select',{className:"tt-form-input",value:semester,onChange:function(ev){setSemester(ev.target.value);},style:{paddingRight:30}},
                            e('option',{value:""},t.all_sem),
                            t.semesters.map(function(s){return e('option',{key:s,value:s},s);})),
                        e('div',{style:{position:"absolute",right:9,top:"50%",transform:"translateY(-50%)",pointerEvents:"none",color:"#9CA3AF"}},e(IcoChevron,{})))),

                /* Stats */
                hasSearch&&e('div',{style:{marginTop:12,display:"flex",alignItems:"center",gap:8,flexWrap:"wrap"}},
                    e('span',{style:{fontSize:12,color:"#9CA3AF"}},
                        filtered.length>0
                            ?[filtered.length+" classes · ",e('strong',{key:"q",style:{color:"#374151"}},search.toUpperCase())]
                            :t.noResults),
                    currentSlot>=0&&currentDay>=0&&e('span',{style:{display:"inline-flex",alignItems:"center",gap:5,fontSize:11,fontWeight:600,color:"var(--crimson)",background:"rgba(139,0,0,.07)",padding:"2px 9px",borderRadius:20}},
                        e('span',{className:"tt-live-dot",style:{width:6,height:6,background:"var(--crimson)",borderRadius:"50%",display:"inline-block"}}),
                        t.live+" · P"+(currentSlot+1)+" · "+SLOTS[currentSlot].start+"–"+SLOTS[currentSlot].end))),

            /* Schedule */
            isMobile
                ?e(MobileView,{lessons:filtered,t:t,currentSlot:currentSlot,currentDay:currentDay,hasSearch:hasSearch})
                :e(DesktopGrid,{lessons:filtered,t:t,currentSlot:currentSlot,currentDay:currentDay,hasSearch:hasSearch}),

            /* Empty state */
            !hasSearch&&e('div',{style:{textAlign:"center",padding:"48px 24px",color:"#B0AA9E",fontSize:14,display:"flex",flexDirection:"column",alignItems:"center",gap:12}},
                e('div',{style:{width:56,height:56,borderRadius:14,background:"rgba(0,35,102,.06)",display:"flex",alignItems:"center",justifyContent:"center",marginBottom:4}},e(IcoCalendar,{size:24})),
                e('div',{style:{fontFamily:"'Cormorant Garamond',serif",fontSize:20,fontWeight:500,color:"#6B7280"}},"Kyrgyz-Turkish Manas University"),
                live.data.length===0&&e('div',{style:{background:"#FFF3CD",border:"1px solid #FFC107",borderRadius:10,padding:"12px 18px",fontSize:12,color:"#856404",maxWidth:500,lineHeight:1.6}},
                    "⚠️ No data loaded. OBIS may require authentication. Ask your admin to log in once via OBIS and the timetable will be populated."),
                e('div',{style:{maxWidth:380,lineHeight:1.7,fontSize:13.5}},t.noSearch),

                /* Quick shortcuts from real data */
                live.groups.length>0&&e('div',{style:{marginTop:8,display:"flex",gap:6,flexWrap:"wrap",justifyContent:"center"}},
                    live.groups.slice(0,10).map(function(g){
                        return e('button',{key:g,onClick:function(){switchView("student");setSearch(g);},
                            style:{fontSize:11,color:"var(--navy)",background:"rgba(0,35,102,.06)",border:"1px solid rgba(0,35,102,.15)",cursor:"pointer",padding:"4px 12px",borderRadius:20,fontWeight:500}},g);
                    })),
                live.faculties.length>0&&e('div',{style:{marginTop:6,display:"flex",gap:6,flexWrap:"wrap",justifyContent:"center"}},
                    live.faculties.map(function(f){
                        return e('span',{key:f,style:{fontSize:10,color:"#6B7280",background:"#F5F3EE",border:"1px solid var(--border)",padding:"3px 10px",borderRadius:20}},f);
                    }))
            )),

        /* Footer */
        e('footer',{style:{borderTop:"1px solid var(--border)",padding:"18px 28px",display:"flex",alignItems:"center",justifyContent:"space-between",flexWrap:"wrap",gap:8,background:"rgba(255,255,255,.6)",backdropFilter:"blur(8px)",position:"relative",zIndex:1}},
            e('div',{style:{fontFamily:"'Cormorant Garamond',serif",fontSize:13,color:"#9CA3AF",fontStyle:"italic"}},"Kyrgyz-Turkish Manas University · Smart Timetable"),
            e('div',{style:{fontSize:11,color:"#C9C5BC"}},"timetable.manas.edu.kg · manas-timetable.vercel.app"))
    );
}

/* ── Mount ───────────────────────────────────────────────── */
(function mount(){
    var root=document.getElementById("tt-root");
    if(root){ ReactDOM.createRoot(root).render(e(TimetableApp,{})); }
    else { setTimeout(mount,50); }
})();