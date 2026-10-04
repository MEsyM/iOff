package com.dualactionwindows.dawdrive

object KidsTriviaQuestionBank {

    enum class Category(val key: String, val labelCs: String) {
        ANIMALS("animals", "Zvířata"),
        MOVIES("movies", "Filmy"),
        CARS("cars", "Auta"),
        SPACE("space", "Vesmír"),
        NATURE("nature", "Příroda"),
        BODY("body", "Lidské tělo"),
        SPORTS("sports", "Sport"),
        FOOTBALL("football", "Fotbal"),
        FOOD_WORLD("food_world", "Jídlo a svět"),
        LOGIC("logic", "Logika"),
        FAIRY_TALES("fairy_tales", "Pohádky"),
        SCHOOL("school", "Škola"),
        CZECHIA("czechia", "České reálie"),
        SONGS("songs", "Písničky")
    }

    data class Question(
        val id: String,
        val category: Category,
        val minAge: Int,
        val maxAge: Int,
        val prompt: String,
        val answers: List<String>,
        val explanation: String
    )

    private data class AnimalFact(
        val name: String,
        val home: String,
        val food: String,
        val young: String?,
        val age: Int
    )

    private data class CzechFact(
        val subject: String,
        val question: String,
        val answer: String,
        val explanation: String,
        val age: Int
    )

    private val bundledQuestions: List<Question> =
        animalQuestions() +
        fairyTaleQuestions() +
        natureQuestions() +
        schoolQuestions() +
        czechiaQuestions()

    val questions: List<Question>
        get() = LiveContentRepository.kidsTriviaQuestions().ifEmpty { bundledQuestions }

    private fun animalQuestions(): List<Question> {
        val facts = listOf(
            AnimalFact("lev","savana","maso","lvíče",6),
            AnimalFact("slon","savana","rostliny","slůně",6),
            AnimalFact("žirafa","savana","listy","žirafátko",6),
            AnimalFact("tučňák","Antarktida","ryby","mládě",6),
            AnimalFact("lední medvěd","Arktida","maso","medvídě",7),
            AnimalFact("delfín","moře","ryby","mládě",7),
            AnimalFact("žralok","moře","ryby",null,7),
            AnimalFact("velbloud","poušť","rostliny","velbloudě",7),
            AnimalFact("klokan","Austrálie","rostliny","mládě",7),
            AnimalFact("panda","Čína","bambus","mládě",7),
            AnimalFact("koala","Austrálie","listy eukalyptu","mládě",7),
            AnimalFact("tygr","Asie","maso","tygříče",7),
            AnimalFact("zebra","Afrika","tráva","hříbě",7),
            AnimalFact("gorila","Afrika","rostliny","mládě",8),
            AnimalFact("krokodýl","řeky a mokřady","maso",null,8),
            AnimalFact("sova","les","drobní živočichové","soví mládě",8),
            AnimalFact("datel","les","hmyz","mládě",8),
            AnimalFact("včela","úl","nektar",null,8),
            AnimalFact("mravenec","mraveniště","různá potrava",null,8),
            AnimalFact("žába","rybník","hmyz","pulec",8),
            AnimalFact("čáp","mokřady","žáby a drobní živočichové","mládě",8),
            AnimalFact("rys","les","maso","rysíče",9),
            AnimalFact("vydra","řeky","ryby","mládě",9),
            AnimalFact("ježek","zahrady a lesy","hmyz","ježče",8),
            AnimalFact("netopýr","jeskyně a půdy","hmyz","mládě",9),
            AnimalFact("orel","hory a krajina","maso","orlí mládě",9),
            AnimalFact("chobotnice","moře","korýši a ryby",null,9),
            AnimalFact("mořský koník","moře","drobní korýši",null,10),
            AnimalFact("kolibřík","Amerika","nektar",null,10),
            AnimalFact("pštros","Afrika","rostliny a drobní živočichové",null,9)
        )

        val result = mutableListOf<Question>()
        facts.forEachIndexed { index, fact ->
            result += Question(
                id = "ka_home_" + index,
                category = Category.ANIMALS,
                minAge = fact.age,
                maxAge = 12,
                prompt = "Kde nejčastěji žije " + fact.name + "?",
                answers = listOf(fact.home),
                explanation = fact.name.replaceFirstChar { it.uppercase() } +
                    " žije hlavně v prostředí: " + fact.home + "."
            )
            result += Question(
                id = "ka_food_" + index,
                category = Category.ANIMALS,
                minAge = fact.age,
                maxAge = 12,
                prompt = "Čím se živí " + fact.name + "?",
                answers = listOf(fact.food),
                explanation = fact.name.replaceFirstChar { it.uppercase() } +
                    " se živí hlavně: " + fact.food + "."
            )
            if (fact.young != null) {
                result += Question(
                    id = "ka_young_" + index,
                    category = Category.ANIMALS,
                    minAge = fact.age,
                    maxAge = 12,
                    prompt = "Jak se říká mláděti zvířete " + fact.name + "?",
                    answers = listOf(fact.young),
                    explanation = "Mládě zvířete " + fact.name + " se nazývá " + fact.young + "."
                )
            }
        }

        result += listOf(
            q("ka001",Category.ANIMALS,6,"Které zvíře má chobot?",listOf("slon"),"Chobot má slon."),
            q("ka002",Category.ANIMALS,6,"Které zvíře má nejdelší krk?",listOf("žirafa","zirafa"),"Žirafa má velmi dlouhý krk."),
            q("ka003",Category.ANIMALS,6,"Které zvíře říká mňau?",listOf("kočka","kocka"),"Mňouká kočka."),
            q("ka004",Category.ANIMALS,6,"Které zvíře štěká?",listOf("pes"),"Štěká pes."),
            q("ka005",Category.ANIMALS,6,"Které zvíře dává mléko a často žije na farmě?",listOf("kráva","krava"),"Kráva dává mléko."),
            q("ka006",Category.ANIMALS,7,"Kolik nohou má pavouk?",listOf("8","osm"),"Pavouk má osm nohou."),
            q("ka007",Category.ANIMALS,7,"Kolik křídel má většina dospělých motýlů?",listOf("4","čtyři","ctyri"),"Motýl má čtyři křídla."),
            q("ka008",Category.ANIMALS,8,"Který savec umí skutečně létat?",listOf("netopýr","netopyr"),"Netopýr je létající savec."),
            q("ka009",Category.ANIMALS,8,"Je delfín ryba, nebo savec?",listOf("savec"),"Delfín je savec."),
            q("ka010",Category.ANIMALS,9,"Který největší živočich dnes žije na Zemi?",listOf("plejtvák obrovský","plejtvak obrovsky","modrá velryba","modra velryba"),"Největším živočichem je plejtvák obrovský."),
            q("ka011",Category.ANIMALS,9,"Jak se nazývá přeměna housenky v motýla?",listOf("metamorfóza","metamorfoza","proměna","promena"),"Jde o metamorfózu neboli proměnu."),
            q("ka012",Category.ANIMALS,10,"Který pták neumí létat a je největším žijícím ptákem?",listOf("pštros","pstros"),"Největším žijícím ptákem je pštros."),
            q("ka013",Category.ANIMALS,10,"Kolik srdcí má chobotnice?",listOf("3","tři","tri"),"Chobotnice má tři srdce."),
            q("ka014",Category.ANIMALS,11,"Jak se nazývá živočich, který jí rostliny i maso?",listOf("všežravec","vsezravec"),"Všežravec jí rostlinnou i živočišnou potravu."),
            q("ka015",Category.ANIMALS,11,"Jak se nazývá věda o zvířatech?",listOf("zoologie"),"Věda o zvířatech se nazývá zoologie.")
        )
        return result
    }

    private fun fairyTaleQuestions(): List<Question> = listOf(
        q("kf001",Category.FAIRY_TALES,6,"Co nesla Červená karkulka babičce?",listOf("košík","kosik","jídlo","jidlo"),"Karkulka nesla babičce košík s jídlem."),
        q("kf002",Category.FAIRY_TALES,6,"Kdo chtěl sníst Červenou karkulku?",listOf("vlk"),"V pohádce ji ohrožuje vlk."),
        q("kf003",Category.FAIRY_TALES,6,"Z čeho byla chaloupka v pohádce Perníková chaloupka?",listOf("perník","pernik"),"Chaloupka byla z perníku."),
        q("kf004",Category.FAIRY_TALES,6,"Jak se jmenují děti z Perníkové chaloupky?",listOf("Jeníček a Mařenka","Jenicek a Marenka","Jeníček Mařenka"),"Děti se jmenují Jeníček a Mařenka."),
        q("kf005",Category.FAIRY_TALES,6,"Co ztratila Popelka při útěku z plesu?",listOf("střevíček","strevicek","botu"),"Popelka ztratila střevíček."),
        q("kf006",Category.FAIRY_TALES,6,"Kolik trpaslíků žije se Sněhurkou?",listOf("7","sedm"),"Se Sněhurkou žije sedm trpaslíků."),
        q("kf007",Category.FAIRY_TALES,6,"Jaké ovoce dostala Sněhurka od zlé královny?",listOf("jablko"),"Bylo to otrávené jablko."),
        q("kf008",Category.FAIRY_TALES,6,"Co měla princezna pod matracemi v pohádce Princezna na hrášku?",listOf("hrášek","hrasek"),"Pod matracemi byl hrášek."),
        q("kf009",Category.FAIRY_TALES,6,"Kolik prasátek je v pohádce Tři prasátka?",listOf("3","tři","tri"),"V pohádce jsou tři prasátka."),
        q("kf010",Category.FAIRY_TALES,6,"Kdo foukal na domečky tří prasátek?",listOf("vlk"),"Domečky se snažil sfouknout vlk."),
        q("kf011",Category.FAIRY_TALES,7,"Jaké dlouhé vlasy má v pohádce Zlatovláska?",listOf("zlaté","zlate"),"Zlatovláska má zlaté vlasy."),
        q("kf012",Category.FAIRY_TALES,7,"Co říká kouzelný hrneček, když má začít vařit?",listOf("hrnečku vař","hrnecku var"),"Kouzelná věta zní Hrnečku, vař."),
        q("kf013",Category.FAIRY_TALES,7,"Co je v pohádce Sůl nad zlato cennější než zlato?",listOf("sůl","sul"),"Pohádka ukazuje, že sůl je pro život velmi důležitá."),
        q("kf014",Category.FAIRY_TALES,7,"Kolik měsíců navštíví Maruška v pohádce O dvanácti měsíčkách?",listOf("12","dvanáct","dvanact"),"V pohádce vystupuje dvanáct měsíců."),
        q("kf015",Category.FAIRY_TALES,7,"Koho unesla liška v pohádce Budulínek?",listOf("Budulínka","budulinka","Budulínek"),"Liška odnesla Budulínka."),
        q("kf016",Category.FAIRY_TALES,7,"Kdo lákal Smolíčka pacholíčka?",listOf("jeskynky","jezinky"),"Smolíčka lákaly jezinky."),
        q("kf017",Category.FAIRY_TALES,8,"Kolik krkavců je v pohádce Sedmero krkavců?",listOf("7","sedm"),"Sedmero znamená sedm."),
        q("kf018",Category.FAIRY_TALES,8,"Jak se jmenuje pohádková bytost, která bývá spojena s rybníkem?",listOf("vodník","vodnik"),"Vodník je tradiční pohádková bytost spojená s vodou."),
        q("kf019",Category.FAIRY_TALES,8,"Jak se jmenuje bytost, která v českých pohádkách často žije v pekle?",listOf("čert","cert"),"V českých pohádkách se často objevuje čert."),
        q("kf020",Category.FAIRY_TALES,8,"Co obvykle splní zlatá rybka?",listOf("přání","prani","přáníčka"),"Zlatá rybka v pohádkách splňuje přání."),
        q("kf021",Category.FAIRY_TALES,8,"Jak se jmenuje pohádka o dívce, která spí sto let?",listOf("Šípková Růženka","Sipkova Ruzenka"),"Jde o Šípkovou Růženku."),
        q("kf022",Category.FAIRY_TALES,8,"Co se stane Pinocchiovi, když lže?",listOf("roste mu nos","zvětší se mu nos","zvetsi se mu nos"),"Při lhaní se mu prodlužuje nos."),
        q("kf023",Category.FAIRY_TALES,9,"Jaké zvíře je Kocour v botách?",listOf("kočka","kocour","kocka"),"Je to kocour."),
        q("kf024",Category.FAIRY_TALES,9,"Jak se jmenuje pohádkový obr, který všechno sní a stále roste?",listOf("Otesánek","Otesanek"),"Jde o Otesánka."),
        q("kf025",Category.FAIRY_TALES,9,"Jaké tři neobvyklé pomocníky má princ v pohádce Dlouhý, Široký a Bystrozraký?",listOf("Dlouhý Široký Bystrozraký","Dlouhy Siroky Bystrozraky"),"Pomocníci se jmenují Dlouhý, Široký a Bystrozraký."),
        q("kf026",Category.FAIRY_TALES,9,"Jak se jmenuje dívka, která má v pohádce kouzelné oříšky?",listOf("Popelka"),"V české filmové pohádce má Popelka tři oříšky."),
        q("kf027",Category.FAIRY_TALES,10,"Který český spisovatel napsal Devatero pohádek?",listOf("Karel Čapek","Karel Capek","Čapek","Capek"),"Devatero pohádek napsal Karel Čapek."),
        q("kf028",Category.FAIRY_TALES,10,"Jak se jmenuje sběratel pohádek spojený s knihou České pohádky a osobou Boženy Němcové?",listOf("Božena Němcová","Bozena Nemcova","Němcová","Nemcova"),"Božena Němcová patří k nejvýznamnějším českým autorkám pohádek."),
        q("kf029",Category.FAIRY_TALES,11,"Co znamená v pohádce zakletí?",listOf("kouzlo","prokletí","prokleti"),"Zakletí je kouzlo, které někoho promění nebo omezuje."),
        q("kf030",Category.FAIRY_TALES,11,"Jak se nazývá závěrečná část pohádky, kde vše dobře dopadne?",listOf("šťastný konec","stastny konec"),"Pohádky často končí šťastným koncem.")
    )

    private fun natureQuestions(): List<Question> = listOf(
        q("kn001",Category.NATURE,6,"Kolik ročních období máme v Česku?",listOf("4","čtyři","ctyri"),"Máme jaro, léto, podzim a zimu."),
        q("kn002",Category.NATURE,6,"Ve kterém ročním období obvykle padá sníh?",listOf("zima","v zimě","v zime"),"Sníh nejčastěji padá v zimě."),
        q("kn003",Category.NATURE,6,"Co potřebuje rostlina k růstu?",listOf("vodu","voda","světlo","svetlo","vodu a světlo"),"Rostliny potřebují hlavně vodu, světlo a živiny."),
        q("kn004",Category.NATURE,6,"Jakou barvu má většina listů v létě?",listOf("zelenou","zelená","zelena"),"V létě jsou listy většinou zelené."),
        q("kn005",Category.NATURE,6,"Jak se jmenuje hvězda, kolem které obíhá Země?",listOf("Slunce","slunce"),"Země obíhá kolem Slunce."),
        q("kn006",Category.NATURE,6,"Co padá z mraků při dešti?",listOf("voda","kapky","dešťové kapky","destove kapky"),"Při dešti padají kapky vody."),
        q("kn007",Category.NATURE,7,"Jak se nazývá voda v pevném skupenství?",listOf("led"),"Pevná voda je led."),
        q("kn008",Category.NATURE,7,"Co vzniká, když se potká sluneční světlo s kapkami vody ve vzduchu?",listOf("duha"),"Může vzniknout duha."),
        q("kn009",Category.NATURE,7,"Který strom má žaludy?",listOf("dub"),"Žaludy rostou na dubu."),
        q("kn010",Category.NATURE,7,"Který jehličnatý strom má dlouhé jehlice a šišky?",listOf("borovice"),"Borovice má dlouhé jehlice a šišky."),
        q("kn011",Category.NATURE,7,"Jak se nazývá místo, kde pramení řeka?",listOf("pramen"),"Řeka začíná u pramene."),
        q("kn012",Category.NATURE,7,"Co je větší: rybník, nebo oceán?",listOf("oceán","ocean"),"Oceán je mnohem větší než rybník."),
        q("kn013",Category.NATURE,8,"Která planeta je nejblíže Slunci?",listOf("Merkur"),"Nejblíže Slunci je Merkur."),
        q("kn014",Category.NATURE,8,"Která planeta je známá svými výraznými prstenci?",listOf("Saturn"),"Saturn má výrazné prstence."),
        q("kn015",Category.NATURE,8,"Kolik planet obíhá kolem Slunce?",listOf("8","osm"),"Ve Sluneční soustavě je osm planet."),
        q("kn016",Category.NATURE,8,"Jak se nazývá přirozená družice Země?",listOf("Měsíc","Mesic"),"Přirozenou družicí Země je Měsíc."),
        q("kn017",Category.NATURE,8,"Jak se nazývá proces, při kterém rostliny využívají světlo k tvorbě živin?",listOf("fotosyntéza","fotosynteza"),"Tento proces se nazývá fotosyntéza."),
        q("kn018",Category.NATURE,8,"Který orgán v těle pumpuje krev?",listOf("srdce"),"Krev pumpuje srdce."),
        q("kn019",Category.NATURE,8,"Kterým orgánem dýcháme?",listOf("plíce","plice"),"Dýcháme pomocí plic."),
        q("kn020",Category.NATURE,8,"Kolik máme běžně smyslů podle základního školního dělení?",listOf("5","pět","pet"),"Základních smyslů se běžně uvádí pět."),
        q("kn021",Category.NATURE,9,"Jak se nazývá plyn, který lidé potřebují k dýchání?",listOf("kyslík","kyslik"),"K dýchání potřebujeme kyslík."),
        q("kn022",Category.NATURE,9,"Jak se nazývá plyn, který rostliny přijímají při fotosyntéze?",listOf("oxid uhličitý","oxid uhlicity","CO2"),"Rostliny přijímají oxid uhličitý."),
        q("kn023",Category.NATURE,9,"Jak se jmenuje nejvyšší vrstva lesa tvořená korunami stromů?",listOf("stromové patro","stromove patro","korunové patro","korunove patro"),"Jde o stromové neboli korunové patro."),
        q("kn024",Category.NATURE,9,"Jak se nazývá změna kapalné vody na vodní páru?",listOf("vypařování","vyparovani"),"Jde o vypařování."),
        q("kn025",Category.NATURE,9,"Jak se nazývá návrat vodní páry na kapalinu?",listOf("kondenzace","zkapalnění","zkapalneni"),"Jde o kondenzaci neboli zkapalnění."),
        q("kn026",Category.NATURE,10,"Jak se nazývá hornina vzniklá ochlazením magmatu?",listOf("vyvřelá hornina","vyvrela hornina","magmatická hornina","magmaticka hornina"),"Jde o vyvřelou neboli magmatickou horninu."),
        q("kn027",Category.NATURE,10,"Která síla nás drží na povrchu Země?",listOf("gravitace","gravitační síla","gravitacni sila"),"Na Zemi nás drží gravitace."),
        q("kn028",Category.NATURE,10,"Jak se nazývá největší orgán lidského těla?",listOf("kůže","kuze"),"Největším orgánem je kůže."),
        q("kn029",Category.NATURE,11,"Jak se nazývá jednotka elektrického proudu?",listOf("ampér","amper"),"Jednotkou elektrického proudu je ampér."),
        q("kn030",Category.NATURE,11,"Jak se nazývá přeměna vody, ledu a páry mezi sebou?",listOf("změna skupenství","zmena skupenstvi"),"Jde o změny skupenství.")
    )

    private fun schoolQuestions(): List<Question> {
        val result = mutableListOf<Question>()

        for (i in 1..60) {
            val a = 2 + (i % 18)
            val b = 1 + (i % 9)
            result += Question(
                id = "ks_add_" + i,
                category = Category.SCHOOL,
                minAge = if (a + b <= 20) 6 else 8,
                maxAge = 12,
                prompt = "Kolik je " + a + " plus " + b + "?",
                answers = listOf((a + b).toString()),
                explanation = a.toString() + " plus " + b + " je " + (a + b) + "."
            )
        }

        for (i in 1..50) {
            val b = 1 + (i % 12)
            val a = b + 5 + (i % 25)
            result += Question(
                id = "ks_sub_" + i,
                category = Category.SCHOOL,
                minAge = if (a <= 20) 7 else 8,
                maxAge = 12,
                prompt = "Kolik je " + a + " minus " + b + "?",
                answers = listOf((a - b).toString()),
                explanation = a.toString() + " minus " + b + " je " + (a - b) + "."
            )
        }

        for (i in 1..45) {
            val a = 2 + (i % 9)
            val b = 2 + ((i * 3) % 9)
            result += Question(
                id = "ks_mul_" + i,
                category = Category.SCHOOL,
                minAge = 8,
                maxAge = 12,
                prompt = "Kolik je " + a + " krát " + b + "?",
                answers = listOf((a * b).toString()),
                explanation = a.toString() + " krát " + b + " je " + (a * b) + "."
            )
        }

        result += listOf(
            q("ks001",Category.SCHOOL,6,"Kolik písmen má slovo pes?",listOf("3","tři","tri"),"Slovo pes má tři písmena."),
            q("ks002",Category.SCHOOL,6,"Jaké je první písmeno české abecedy?",listOf("A","á","a"),"První písmeno je A."),
            q("ks003",Category.SCHOOL,6,"Co je opak slova velký?",listOf("malý","maly"),"Opak slova velký je malý."),
            q("ks004",Category.SCHOOL,7,"Kolik slabik má slovo kočka?",listOf("2","dvě","dve"),"Kočka má dvě slabiky: ko-čka."),
            q("ks005",Category.SCHOOL,7,"Jaké znaménko píšeme na konci otázky?",listOf("otazník","otaznik"),"Na konci otázky píšeme otazník."),
            q("ks006",Category.SCHOOL,7,"Jak se jmenuje číslo, které je o jedna větší než devět?",listOf("10","deset"),"Po devítce následuje deset."),
            q("ks007",Category.SCHOOL,8,"Kolik centimetrů má jeden metr?",listOf("100","sto"),"Jeden metr má sto centimetrů."),
            q("ks008",Category.SCHOOL,8,"Kolik minut má jedna hodina?",listOf("60","šedesát","sedesat"),"Jedna hodina má šedesát minut."),
            q("ks009",Category.SCHOOL,8,"Kolik dní má běžný týden?",listOf("7","sedm"),"Týden má sedm dní."),
            q("ks010",Category.SCHOOL,8,"Jaké slovní druhy jsou pes, strom a škola?",listOf("podstatná jména","podstatna jmena"),"Jsou to podstatná jména."),
            q("ks011",Category.SCHOOL,9,"Kolik je polovina ze 100?",listOf("50","padesát","padesat"),"Polovina ze sta je padesát."),
            q("ks012",Category.SCHOOL,9,"Kolik je čtvrtina z 20?",listOf("5","pět","pet"),"Čtvrtina z dvaceti je pět."),
            q("ks013",Category.SCHOOL,9,"Jaký slovní druh je slovo rychle?",listOf("příslovce","prislovce"),"Rychle je příslovce."),
            q("ks014",Category.SCHOOL,9,"Jaký slovní druh je slovo krásný?",listOf("přídavné jméno","pridavne jmeno"),"Krásný je přídavné jméno."),
            q("ks015",Category.SCHOOL,10,"Kolik stupňů má pravý úhel?",listOf("90","devadesát","devadesat"),"Pravý úhel má devadesát stupňů."),
            q("ks016",Category.SCHOOL,10,"Kolik stran má šestiúhelník?",listOf("6","šest","sest"),"Šestiúhelník má šest stran."),
            q("ks017",Category.SCHOOL,10,"Jak se nazývá výsledek násobení?",listOf("součin"),"Výsledek násobení se nazývá součin."),
            q("ks018",Category.SCHOOL,10,"Jak se nazývá výsledek dělení?",listOf("podíl","podil"),"Výsledek dělení se nazývá podíl."),
            q("ks019",Category.SCHOOL,11,"Kolik je 25 procent ze 100?",listOf("25","dvacet pět","dvacet pet"),"Dvacet pět procent ze sta je dvacet pět."),
            q("ks020",Category.SCHOOL,11,"Kolik milimetrů má jeden centimetr?",listOf("10","deset"),"Jeden centimetr má deset milimetrů."),
            q("ks021",Category.SCHOOL,11,"Jak se nazývá nejdelší strana pravoúhlého trojúhelníku?",listOf("přepona","prepona"),"Nejdelší strana pravoúhlého trojúhelníku je přepona."),
            q("ks022",Category.SCHOOL,11,"Jak se nazývá číslo nahoře ve zlomku?",listOf("čitatel","citatel"),"Horní číslo ve zlomku je čitatel."),
            q("ks023",Category.SCHOOL,12,"Jak se nazývá číslo dole ve zlomku?",listOf("jmenovatel"),"Dolní číslo ve zlomku je jmenovatel."),
            q("ks024",Category.SCHOOL,12,"Jaký je obvod čtverce se stranou 5 centimetrů?",listOf("20","20 centimetrů","dvacet"),"Obvod čtverce je čtyřikrát délka strany, tedy 20 centimetrů."),
            q("ks025",Category.SCHOOL,12,"Kolik je 3 na druhou?",listOf("9","devět","devet"),"Tři na druhou je devět.")
        )
        return result
    }

    private fun czechiaQuestions(): List<Question> {
        val facts = listOf(
            CzechFact("Praha","Jaké je hlavní město České republiky?","Praha","Hlavním městem České republiky je Praha.",6),
            CzechFact("Vltava","Která řeka protéká Prahou?","Vltava","Prahou protéká Vltava.",7),
            CzechFact("Labe","Která velká česká řeka teče přes Ústí nad Labem?","Labe","Přes Ústí nad Labem teče Labe.",9),
            CzechFact("Sněžka","Jak se jmenuje nejvyšší hora České republiky?","Sněžka","Nejvyšší českou horou je Sněžka.",7),
            CzechFact("Krkonoše","Ve kterém pohoří leží Sněžka?","Krkonoše","Sněžka leží v Krkonoších.",8),
            CzechFact("Brno","Jaké je druhé největší město České republiky?","Brno","Druhým největším městem je Brno.",8),
            CzechFact("Ostrava","Které velké město leží na severovýchodě Česka a je známé hornickou a průmyslovou historií?","Ostrava","Jde o Ostravu.",9),
            CzechFact("Plzeň","Které české město je známé pivem Pilsner?","Plzeň","Pilsner je spojený s Plzní.",9),
            CzechFact("Český lev","Jaké zvíře je na velkém státním znaku Česka symbolem Čech?","lev","Symbolem Čech je dvouocasý lev.",9),
            CzechFact("koruna","Jak se jmenuje česká měna?","koruna","Českou měnou je koruna česká.",7),
            CzechFact("Pražský hrad","Jak se jmenuje velký hradní areál nad Prahou, kde sídlí prezident?","Pražský hrad","Jde o Pražský hrad.",8),
            CzechFact("Karlův most","Jak se jmenuje slavný historický most přes Vltavu v Praze?","Karlův most","Slavným pražským mostem je Karlův most.",8),
            CzechFact("Ještěd","Jak se jmenuje hora s výrazným televizním vysílačem nad Libercem?","Ještěd","Nad Libercem stojí Ještěd.",10),
            CzechFact("Macocha","Jak se jmenuje známá propast v Moravském krasu?","Macocha","Známou propastí Moravského krasu je Macocha.",10),
            CzechFact("Lipno","Jak se jmenuje velká přehradní nádrž na Vltavě v jižních Čechách?","Lipno","Jde o vodní nádrž Lipno.",10),
            CzechFact("Morava","Jak se jmenuje historická země tvořící východní část Česka?","Morava","Východní část Česka tvoří z velké části Morava.",8),
            CzechFact("Čechy","Jak se jmenuje historická země tvořící západní část Česka?","Čechy","Západní část Česka tvoří Čechy.",8),
            CzechFact("Olomouc","Které moravské město je známé sloupem Nejsvětější Trojice a orlojem?","Olomouc","Jde o Olomouc.",11),
            CzechFact("Český Krumlov","Které jihočeské historické město s hradem je zapsané na seznamu UNESCO?","Český Krumlov","Jde o Český Krumlov.",10),
            CzechFact("Kutná Hora","Které středočeské město je známé chrámem svaté Barbory?","Kutná Hora","Chrám svaté Barbory stojí v Kutné Hoře.",11),
            CzechFact("Pardubice","Které české město je známé perníkem a Velkou pardubickou?","Pardubice","Jde o Pardubice.",9),
            CzechFact("Zlín","Které město je silně spojené s firmou Baťa?","Zlín","Firma Baťa je historicky spojená se Zlínem.",9),
            CzechFact("Šumava","Jak se jmenuje pohoří na jihozápadě Česka u hranic s Německem a Rakouskem?","Šumava","Jde o Šumavu.",10),
            CzechFact("Orlík","Jak se jmenuje přehrada na Vltavě s velkou vodní nádrží ve středních Čechách?","Orlík","Jde o vodní nádrž Orlík.",11),
            CzechFact("Moravský kras","Jak se jmenuje krasová oblast severně od Brna s Punkevními jeskyněmi?","Moravský kras","Punkevní jeskyně leží v Moravském krasu.",10)
        )

        val result = facts.mapIndexed { index, fact ->
            Question(
                id = "kc_fact_" + index,
                category = Category.CZECHIA,
                minAge = fact.age,
                maxAge = 12,
                prompt = fact.question,
                answers = listOf(fact.answer),
                explanation = fact.explanation
            )
        }.toMutableList()

        result += listOf(
            q("kc001",Category.CZECHIA,6,"Jaké barvy má česká vlajka?",listOf("bílá červená modrá","bila cervena modra","bílá, červená a modrá"),"Česká vlajka je bílá, červená a modrá."),
            q("kc002",Category.CZECHIA,6,"Jakým jazykem se mluví v České republice?",listOf("česky","čeština","cestina"),"Úředním jazykem je čeština."),
            q("kc003",Category.CZECHIA,7,"Na kterém kontinentu leží Česká republika?",listOf("Evropa","v Evropě","v Evrope"),"Česko leží v Evropě."),
            q("kc004",Category.CZECHIA,7,"Má Česká republika moře?",listOf("ne","nemá","nema"),"Česká republika nemá přístup k moři."),
            q("kc005",Category.CZECHIA,8,"Kolik sousedních států má Česká republika?",listOf("4","čtyři","ctyri"),"Česko sousedí s Německem, Polskem, Slovenskem a Rakouskem."),
            q("kc006",Category.CZECHIA,8,"Který stát leží východně od České republiky a má hlavní město Bratislavu?",listOf("Slovensko"),"Jde o Slovensko."),
            q("kc007",Category.CZECHIA,8,"Který stát leží severně od České republiky a má hlavní město Varšavu?",listOf("Polsko"),"Jde o Polsko."),
            q("kc008",Category.CZECHIA,8,"Který stát leží jižně od České republiky a má hlavní město Vídeň?",listOf("Rakousko"),"Jde o Rakousko."),
            q("kc009",Category.CZECHIA,8,"Který stát leží západně od České republiky a má hlavní město Berlín?",listOf("Německo","Nemecko"),"Jde o Německo."),
            q("kc010",Category.CZECHIA,9,"Kdy slavíme Den vzniku samostatného československého státu?",listOf("28. října","28 října","dvacátého osmého října","28.10."),"Státní svátek připadá na 28. října."),
            q("kc011",Category.CZECHIA,9,"Jak se jmenuje česká státní hymna?",listOf("Kde domov můj","Kde domov muj"),"Česká hymna se jmenuje Kde domov můj."),
            q("kc012",Category.CZECHIA,10,"Jak se jmenuje nejdelší řeka, která teče pouze územím Česka?",listOf("Vltava"),"Nejdelší řekou tekoucí pouze Českem je Vltava."),
            q("kc013",Category.CZECHIA,10,"Ve kterém městě najdeme hrad Špilberk?",listOf("Brno","v Brně","v Brne"),"Špilberk stojí v Brně."),
            q("kc014",Category.CZECHIA,10,"Ve kterém městě najdeme zoologickou zahradu známou chovem goril a velkým areálem v Troji?",listOf("Praha","v Praze"),"Jde o Zoo Praha v Troji."),
            q("kc015",Category.CZECHIA,11,"Jak se nazývá nejstarší česká univerzita založená roku 1348?",listOf("Univerzita Karlova","Karlova univerzita"),"Nejstarší českou univerzitou je Univerzita Karlova.")
        )
        return result
    }

    private fun q(
        id: String,
        category: Category,
        minAge: Int,
        prompt: String,
        answers: List<String>,
        explanation: String
    ) = Question(
        id = id,
        category = category,
        minAge = minAge,
        maxAge = 12,
        prompt = prompt,
        answers = answers,
        explanation = explanation
    )
}
