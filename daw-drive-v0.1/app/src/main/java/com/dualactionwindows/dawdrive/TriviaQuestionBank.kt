package com.dualactionwindows.dawdrive

object TriviaQuestionBank {

    enum class Category(val key: String) {
        GEOGRAPHY("geography"),
        SCIENCE("science"),
        HISTORY("history"),
        GENERAL("general"),
        TECHNOLOGY("technology"),
        NATURE("nature"),
        SPORTS("sports"),
        CULTURE("culture"),
        MOVIES("movies"),
        CARS("cars"),
        NUMBERS("numbers")
    }

    data class LocalizedQuestion(
        val id: String,
        val difficulty: Int,
        val category: Category,
        val promptEn: String,
        val promptCs: String,
        val answersEn: List<String>,
        val answersCs: List<String>,
        val explanationEn: String,
        val explanationCs: String
    )

    val questions: List<LocalizedQuestion> = listOf(
        q("geo001",1,Category.GEOGRAPHY,"What is the capital of France?","Jaké je hlavní město Francie?",listOf("paris"),listOf("pariz","paříž"),"Paris is the capital of France.","Paříž je hlavní město Francie."),
        q("geo002",1,Category.GEOGRAPHY,"What is the capital of Italy?","Jaké je hlavní město Itálie?",listOf("rome"),listOf("rim","řím"),"Rome is the capital of Italy.","Řím je hlavní město Itálie."),
        q("geo003",1,Category.GEOGRAPHY,"Which ocean is the largest?","Který oceán je největší?",listOf("pacific","pacific ocean"),listOf("tichy ocean","tichý oceán","pacifik"),"The Pacific is Earth's largest ocean.","Tichý oceán je největší oceán na Zemi."),
        q("geo004",1,Category.GEOGRAPHY,"Which country has the city of Barcelona?","Ve které zemi leží Barcelona?",listOf("spain"),listOf("spanelsko","španělsko"),"Barcelona is in Spain.","Barcelona leží ve Španělsku."),
        q("geo005",1,Category.GEOGRAPHY,"What is the capital of Germany?","Jaké je hlavní město Německa?",listOf("berlin"),listOf("berlin"),"Berlin is the capital of Germany.","Berlín je hlavní město Německa."),
        q("geo006",2,Category.GEOGRAPHY,"What is the capital of Australia?","Jaké je hlavní město Austrálie?",listOf("canberra"),listOf("canberra"),"Canberra is the capital of Australia.","Canberra je hlavní město Austrálie."),
        q("geo007",2,Category.GEOGRAPHY,"What is the capital of Canada?","Jaké je hlavní město Kanady?",listOf("ottawa"),listOf("ottawa"),"Ottawa is the capital of Canada.","Ottawa je hlavní město Kanady."),
        q("geo008",2,Category.GEOGRAPHY,"Which river runs through Budapest?","Která řeka protéká Budapeští?",listOf("danube","danube river"),listOf("dunaj"),"The Danube runs through Budapest.","Budapeští protéká Dunaj."),
        q("geo009",2,Category.GEOGRAPHY,"What is the capital of Portugal?","Jaké je hlavní město Portugalska?",listOf("lisbon"),listOf("lisabon"),"Lisbon is the capital of Portugal.","Lisabon je hlavní město Portugalska."),
        q("geo010",2,Category.GEOGRAPHY,"Which country has Amsterdam as its capital?","Která země má hlavní město Amsterdam?",listOf("netherlands","the netherlands","holland"),listOf("nizozemsko","holandsko"),"Amsterdam is the capital of the Netherlands.","Amsterdam je hlavní město Nizozemska."),
        q("geo011",3,Category.GEOGRAPHY,"What is the capital of New Zealand?","Jaké je hlavní město Nového Zélandu?",listOf("wellington"),listOf("wellington"),"Wellington is the capital of New Zealand.","Wellington je hlavní město Nového Zélandu."),
        q("geo012",3,Category.GEOGRAPHY,"Which country borders both Spain and France?","Která země leží mezi Španělskem a Francií?",listOf("andorra"),listOf("andorra"),"Andorra borders Spain and France.","Andorra sousedí se Španělskem i Francií."),
        q("geo013",3,Category.GEOGRAPHY,"What is the capital of Norway?","Jaké je hlavní město Norska?",listOf("oslo"),listOf("oslo"),"Oslo is the capital of Norway.","Oslo je hlavní město Norska."),
        q("geo014",3,Category.GEOGRAPHY,"Which sea separates Europe and Africa?","Které moře odděluje Evropu a Afriku?",listOf("mediterranean","mediterranean sea"),listOf("stredozemni more","středozemní moře"),"The Mediterranean separates much of Europe and Africa.","Evropu a Afriku z velké části odděluje Středozemní moře."),
        q("geo015",4,Category.GEOGRAPHY,"What is the capital of Kazakhstan?","Jaké je hlavní město Kazachstánu?",listOf("astana"),listOf("astana"),"Astana is the capital of Kazakhstan.","Astana je hlavní město Kazachstánu."),
        q("geo016",4,Category.GEOGRAPHY,"Which African country has Addis Ababa as its capital?","Která africká země má hlavní město Addis Abeba?",listOf("ethiopia"),listOf("etiopie"),"Addis Ababa is Ethiopia's capital.","Addis Abeba je hlavní město Etiopie."),
        q("geo017",4,Category.GEOGRAPHY,"Which river flows through Prague?","Která řeka protéká Prahou?",listOf("vltava","moldau"),listOf("vltava"),"The Vltava flows through Prague.","Prahou protéká Vltava."),
        q("geo018",5,Category.GEOGRAPHY,"What is the capital of Bhutan?","Jaké je hlavní město Bhútánu?",listOf("thimphu"),listOf("thimphu","thimpu"),"Thimphu is the capital of Bhutan.","Thimphu je hlavní město Bhútánu."),
        q("geo019",5,Category.GEOGRAPHY,"Which country contains the Atacama Desert?","Ve které zemi leží poušť Atacama?",listOf("chile"),listOf("chile"),"Most of the Atacama Desert lies in Chile.","Většina pouště Atacama leží v Chile."),
        q("geo020",5,Category.GEOGRAPHY,"Which strait separates Asia and North America?","Který průliv odděluje Asii a Severní Ameriku?",listOf("bering strait","bering"),listOf("beringuv pruliv","beringův průliv"),"The Bering Strait separates Asia and North America.","Asii a Severní Ameriku odděluje Beringův průliv."),

        q("sci001",1,Category.SCIENCE,"Which planet is known as the Red Planet?","Která planeta je známá jako rudá planeta?",listOf("mars"),listOf("mars"),"Mars appears red because of iron oxides.","Mars vypadá rudě kvůli oxidům železa."),
        q("sci002",1,Category.SCIENCE,"What gas do humans need to breathe?","Jaký plyn lidé potřebují k dýchání?",listOf("oxygen"),listOf("kyslik","kyslík"),"Humans need oxygen for respiration.","Lidé potřebují k dýchání kyslík."),
        q("sci003",1,Category.SCIENCE,"How many legs does an insect have?","Kolik nohou má hmyz?",listOf("6","six"),listOf("6","sest","šest"),"Insects have six legs.","Hmyz má šest nohou."),
        q("sci004",1,Category.SCIENCE,"What is H2O commonly called?","Jak se běžně nazývá H2O?",listOf("water"),listOf("voda"),"H2O is water.","H2O je voda."),
        q("sci005",2,Category.SCIENCE,"What is the chemical symbol for gold?","Jaká je chemická značka zlata?",listOf("au"),listOf("au"),"Gold has the symbol Au.","Zlato má značku Au."),
        q("sci006",2,Category.SCIENCE,"What force keeps planets in orbit?","Jaká síla udržuje planety na oběžné dráze?",listOf("gravity","gravitation"),listOf("gravitace","gravitacni sila","gravitační síla"),"Gravity keeps planets in orbit.","Planety na oběžné dráze udržuje gravitace."),
        q("sci007",2,Category.SCIENCE,"What is the largest organ in the human body?","Jaký je největší orgán lidského těla?",listOf("skin","the skin"),listOf("kuze","kůže"),"The skin is the body's largest organ.","Kůže je největší orgán lidského těla."),
        q("sci008",2,Category.SCIENCE,"What gas makes up most of Earth's atmosphere?","Který plyn tvoří největší část zemské atmosféry?",listOf("nitrogen"),listOf("dusik","dusík"),"Nitrogen makes up about 78 percent of the atmosphere.","Dusík tvoří asi 78 procent atmosféry."),
        q("sci009",3,Category.SCIENCE,"What is the atomic number of carbon?","Jaké je protonové číslo uhlíku?",listOf("6","six"),listOf("6","sest","šest"),"Carbon has atomic number 6.","Uhlík má protonové číslo 6."),
        q("sci010",3,Category.SCIENCE,"What part of a cell contains most genetic material?","Která část buňky obsahuje většinu genetické informace?",listOf("nucleus","the nucleus"),listOf("jadro","jádro"),"Most genetic material is in the nucleus.","Většina genetické informace je v buněčném jádře."),
        q("sci011",3,Category.SCIENCE,"What is the SI unit of electrical resistance?","Jaká je jednotka elektrického odporu?",listOf("ohm","ohms"),listOf("ohm","omy"),"Resistance is measured in ohms.","Elektrický odpor se měří v ohmech."),
        q("sci012",3,Category.SCIENCE,"What is the speed of light approximately in kilometers per second?","Jaká je přibližná rychlost světla v kilometrech za sekundu?",listOf("300000","three hundred thousand"),listOf("300000","tri sta tisic","tři sta tisíc"),"Light travels about 300,000 kilometers per second.","Světlo se šíří přibližně 300 tisíc kilometrů za sekundu."),
        q("sci013",4,Category.SCIENCE,"Which scientist formulated the three laws of motion?","Který vědec formuloval tři pohybové zákony?",listOf("isaac newton","newton"),listOf("isaac newton","newton"),"Isaac Newton formulated the three laws of motion.","Tři pohybové zákony formuloval Isaac Newton."),
        q("sci014",4,Category.SCIENCE,"What particle has a negative electric charge?","Která částice má záporný elektrický náboj?",listOf("electron"),listOf("elektron"),"The electron has negative charge.","Elektron má záporný náboj."),
        q("sci015",4,Category.SCIENCE,"What is the pH of pure water at room temperature?","Jaké pH má čistá voda při pokojové teplotě?",listOf("7","seven"),listOf("7","sedm"),"Pure water is approximately pH 7.","Čistá voda má přibližně pH 7."),
        q("sci016",4,Category.SCIENCE,"Which blood cells primarily carry oxygen?","Které krevní buňky hlavně přenášejí kyslík?",listOf("red blood cells","erythrocytes"),listOf("cervene krvinky","červené krvinky","erytrocyty"),"Red blood cells carry oxygen using hemoglobin.","Červené krvinky přenášejí kyslík pomocí hemoglobinu."),
        q("sci017",5,Category.SCIENCE,"What is Avogadro's constant approximately?","Jaká je přibližná hodnota Avogadrovy konstanty?",listOf("6.022 times 10 to the 23","6.022e23","6.02e23"),listOf("6.022 krat 10 na 23","6.02 krat 10 na 23"),"Avogadro's constant is about 6.022 times 10 to the 23 per mole.","Avogadrova konstanta je přibližně 6,022 krát 10 na 23 na mol."),
        q("sci018",5,Category.SCIENCE,"Which law relates pressure and volume of a gas at constant temperature?","Který zákon popisuje vztah tlaku a objemu plynu při stálé teplotě?",listOf("boyle's law","boyle law"),listOf("boyleuv zakon","boylův zákon"),"Boyle's law relates pressure and volume at constant temperature.","Boylův zákon popisuje vztah tlaku a objemu při stálé teplotě."),
        q("sci019",5,Category.SCIENCE,"Which organelle produces most ATP in eukaryotic cells?","Která organela vytváří většinu ATP v eukaryotické buňce?",listOf("mitochondria","mitochondrion"),listOf("mitochondrie"),"Mitochondria generate most cellular ATP.","Většinu ATP vytvářejí mitochondrie."),
        q("sci020",5,Category.SCIENCE,"What is the name of the boundary around a black hole beyond which light cannot escape?","Jak se nazývá hranice kolem černé díry, za kterou nemůže uniknout světlo?",listOf("event horizon"),listOf("horizont udalosti","horizont událostí"),"That boundary is the event horizon.","Tato hranice se nazývá horizont událostí."),

        q("hist001",1,Category.HISTORY,"The pyramids of Giza are in which country?","V které zemi stojí pyramidy v Gíze?",listOf("egypt"),listOf("egypt"),"The Giza pyramids are in Egypt.","Pyramidy v Gíze jsou v Egyptě."),
        q("hist002",1,Category.HISTORY,"Who was the first person to walk on the Moon?","Kdo byl první člověk na Měsíci?",listOf("neil armstrong","armstrong"),listOf("neil armstrong","armstrong"),"Neil Armstrong walked on the Moon in 1969.","Neil Armstrong vstoupil na Měsíc v roce 1969."),
        q("hist003",1,Category.HISTORY,"In which century did World War Two occur?","Ve kterém století proběhla druhá světová válka?",listOf("20th","twentieth","20"),listOf("20","dvacate","dvacáté"),"World War Two occurred in the twentieth century.","Druhá světová válka proběhla ve dvacátém století."),
        q("hist004",2,Category.HISTORY,"In which year did World War Two end?","V kterém roce skončila druhá světová válka?",listOf("1945","nineteen forty five"),listOf("1945","tisic devet set ctyricet pet","tisíc devět set čtyřicet pět"),"World War Two ended in 1945.","Druhá světová válka skončila v roce 1945."),
        q("hist005",2,Category.HISTORY,"Which civilization built Machu Picchu?","Která civilizace vybudovala Machu Picchu?",listOf("inca","incas","the inca"),listOf("inkove","inkové","inka"),"Machu Picchu was built by the Inca.","Machu Picchu vybudovali Inkové."),
        q("hist006",2,Category.HISTORY,"Who wrote the Declaration of Independence draft in the United States?","Kdo napsal hlavní návrh americké Deklarace nezávislosti?",listOf("thomas jefferson","jefferson"),listOf("thomas jefferson","jefferson"),"Thomas Jefferson wrote the principal draft.","Hlavní návrh napsal Thomas Jefferson."),
        q("hist007",3,Category.HISTORY,"Which city was buried by Mount Vesuvius in 79 AD?","Které město pohřbil Vesuv v roce 79 našeho letopočtu?",listOf("pompeii"),listOf("pompeje"),"Pompeii was buried by the eruption.","Erupce pohřbila Pompeje."),
        q("hist008",3,Category.HISTORY,"Who was the first emperor of Rome?","Kdo byl prvním římským císařem?",listOf("augustus","octavian","octavian augustus"),listOf("augustus","oktavian","octavianus"),"Augustus was the first Roman emperor.","Prvním římským císařem byl Augustus."),
        q("hist009",3,Category.HISTORY,"Which war was fought between the North and South regions of the United States?","Jaká válka proběhla mezi severem a jihem Spojených států?",listOf("american civil war","civil war"),listOf("americka obcanska valka","americká občanská válka","obcanska valka"),"It was the American Civil War.","Byla to americká občanská válka."),
        q("hist010",4,Category.HISTORY,"Which treaty ended World War One between Germany and the Allied powers?","Která smlouva ukončila první světovou válku mezi Německem a spojenci?",listOf("treaty of versailles","versailles"),listOf("versailleska smlouva","versailleská smlouva","versailles"),"The Treaty of Versailles was signed in 1919.","Versailleská smlouva byla podepsána v roce 1919."),
        q("hist011",4,Category.HISTORY,"Who was the British prime minister for most of World War Two?","Kdo byl britským premiérem po většinu druhé světové války?",listOf("winston churchill","churchill"),listOf("winston churchill","churchill"),"Winston Churchill led Britain for most of the war.","Británii po většinu války vedl Winston Churchill."),
        q("hist012",4,Category.HISTORY,"What empire was ruled by Mansa Musa?","Které říši vládl Mansa Musa?",listOf("mali empire","mali"),listOf("rise mali","říše mali","mali"),"Mansa Musa ruled the Mali Empire.","Mansa Musa vládl říši Mali."),
        q("hist013",5,Category.HISTORY,"Which battle in 1815 ended Napoleon's rule?","Která bitva v roce 1815 ukončila Napoleonovu vládu?",listOf("waterloo","battle of waterloo"),listOf("waterloo","bitva u waterloo"),"Napoleon was defeated at Waterloo.","Napoleon byl poražen u Waterloo."),
        q("hist014",5,Category.HISTORY,"Who issued the Edict of Milan in 313 with Licinius?","Kdo spolu s Liciniem vydal Milánský edikt roku 313?",listOf("constantine","constantine the great"),listOf("konstantin","konstantin veliky","konstantin veliký"),"Constantine issued the Edict of Milan with Licinius.","Milánský edikt vydal s Liciniem Konstantin."),
        q("hist015",5,Category.HISTORY,"Which dynasty built much of the current Great Wall of China?","Která dynastie vybudovala velkou část dnešní Velké čínské zdi?",listOf("ming","ming dynasty"),listOf("ming","dynastie ming"),"Much of today's wall dates to the Ming dynasty.","Velká část dnešní zdi pochází z dynastie Ming."),

        q("gen001",1,Category.GENERAL,"How many days are in a leap year?","Kolik dní má přestupný rok?",listOf("366","three hundred sixty six","three hundred and sixty six"),listOf("366","tri sta sedesat sest","tři sta šedesát šest"),"A leap year has 366 days.","Přestupný rok má 366 dní."),
        q("gen002",1,Category.GENERAL,"How many sides does a hexagon have?","Kolik stran má šestiúhelník?",listOf("6","six"),listOf("6","sest","šest"),"A hexagon has six sides.","Šestiúhelník má šest stran."),
        q("gen003",1,Category.GENERAL,"How many minutes are in one hour?","Kolik minut má jedna hodina?",listOf("60","sixty"),listOf("60","sedesat","šedesát"),"One hour has 60 minutes.","Jedna hodina má 60 minut."),
        q("gen004",2,Category.GENERAL,"What is the square root of 144?","Jaká je odmocnina ze 144?",listOf("12","twelve"),listOf("12","dvanact","dvanáct"),"The square root of 144 is 12.","Odmocnina ze 144 je 12."),
        q("gen005",2,Category.GENERAL,"What is 25 percent of 200?","Kolik je 25 procent z 200?",listOf("50","fifty"),listOf("50","padesat","padesát"),"Twenty-five percent of 200 is 50.","Dvacet pět procent z 200 je 50."),
        q("gen006",2,Category.GENERAL,"How many degrees are in a right angle?","Kolik stupňů má pravý úhel?",listOf("90","ninety"),listOf("90","devadesat","devadesát"),"A right angle is 90 degrees.","Pravý úhel má 90 stupňů."),
        q("gen007",3,Category.GENERAL,"What is 17 squared?","Kolik je 17 na druhou?",listOf("289","two hundred eighty nine","two hundred and eighty nine"),listOf("289","dve ste osmdesat devet","dvě stě osmdesát devět"),"Seventeen squared is 289.","Sedmnáct na druhou je 289."),
        q("gen008",3,Category.GENERAL,"How many prime numbers are below 10?","Kolik prvočísel je menších než 10?",listOf("4","four"),listOf("4","ctyri","čtyři"),"They are 2, 3, 5 and 7.","Jsou to 2, 3, 5 a 7."),
        q("gen009",4,Category.GENERAL,"What is 15 percent of 200?","Kolik je 15 procent z 200?",listOf("30","thirty"),listOf("30","tricet","třicet"),"Fifteen percent of 200 is 30.","Patnáct procent z 200 je 30."),
        q("gen010",4,Category.GENERAL,"What is the next prime number after 19?","Jaké je další prvočíslo po 19?",listOf("23","twenty three"),listOf("23","dvacet tri","dvacet tři"),"The next prime after 19 is 23.","Další prvočíslo po 19 je 23."),
        q("gen011",5,Category.GENERAL,"What is 12 factorial divided by 11 factorial?","Kolik je 12 faktoriál děleno 11 faktoriál?",listOf("12","twelve"),listOf("12","dvanact","dvanáct"),"The factorials cancel to 12.","Faktoriály se zkrátí na 12."),
        q("gen012",5,Category.GENERAL,"What is the binary representation of decimal ten?","Jak se zapíše desítka v binární soustavě?",listOf("1010","one zero one zero"),listOf("1010","jedna nula jedna nula"),"Decimal ten is binary 1010.","Desítka je v binární soustavě 1010."),

        q("tech001",1,Category.TECHNOLOGY,"What does GPS stand for?","Co znamená zkratka GPS?",listOf("global positioning system"),listOf("global positioning system","globalni polohovy system"),"GPS means Global Positioning System.","GPS znamená Global Positioning System."),
        q("tech002",1,Category.TECHNOLOGY,"Which company develops Android?","Která společnost vyvíjí Android?",listOf("google"),listOf("google"),"Android is developed by Google and the Open Handset Alliance.","Android vyvíjí Google a Open Handset Alliance."),
        q("tech003",2,Category.TECHNOLOGY,"What does CPU stand for?","Co znamená zkratka CPU?",listOf("central processing unit"),listOf("central processing unit","centralni procesorova jednotka"),"CPU means Central Processing Unit.","CPU znamená Central Processing Unit."),
        q("tech004",2,Category.TECHNOLOGY,"What protocol is commonly used to securely browse websites?","Jaký protokol se běžně používá pro bezpečné prohlížení webu?",listOf("https"),listOf("https"),"HTTPS encrypts web traffic.","HTTPS šifruje webový provoz."),
        q("tech005",3,Category.TECHNOLOGY,"What does RAM stand for?","Co znamená zkratka RAM?",listOf("random access memory"),listOf("random access memory"),"RAM means Random Access Memory.","RAM znamená Random Access Memory."),
        q("tech006",3,Category.TECHNOLOGY,"Which version control system was created by Linus Torvalds?","Který systém pro správu verzí vytvořil Linus Torvalds?",listOf("git"),listOf("git"),"Linus Torvalds created Git.","Linus Torvalds vytvořil Git."),
        q("tech007",4,Category.TECHNOLOGY,"What port does HTTPS use by default?","Jaký port standardně používá HTTPS?",listOf("443","four hundred forty three"),listOf("443","ctyri sta ctyricet tri","čtyři sta čtyřicet tři"),"HTTPS normally uses port 443.","HTTPS standardně používá port 443."),
        q("tech008",4,Category.TECHNOLOGY,"What does SQL stand for?","Co znamená zkratka SQL?",listOf("structured query language"),listOf("structured query language"),"SQL means Structured Query Language.","SQL znamená Structured Query Language."),
        q("tech009",5,Category.TECHNOLOGY,"Which data structure uses first in, first out ordering?","Která datová struktura používá pořadí první dovnitř, první ven?",listOf("queue"),listOf("fronta","queue"),"A queue is FIFO.","Fronta používá princip FIFO."),
        q("tech010",5,Category.TECHNOLOGY,"What is the time complexity of binary search?","Jaká je časová složitost binárního vyhledávání?",listOf("o log n","log n","logarithmic"),listOf("o log n","log n","logaritmicka","logaritmická"),"Binary search runs in logarithmic time.","Binární vyhledávání má logaritmickou časovou složitost."),

        q("nat001",1,Category.NATURE,"What is the largest land animal?","Jaké je největší suchozemské zvíře?",listOf("african elephant","elephant"),listOf("slon africký","slon"),"The African elephant is the largest land animal.","Slon africký je největší suchozemské zvíře."),
        q("nat002",1,Category.NATURE,"What do bees collect from flowers to make honey?","Co včely sbírají z květů pro výrobu medu?",listOf("nectar"),listOf("nektar"),"Bees collect nectar.","Včely sbírají nektar."),
        q("nat003",2,Category.NATURE,"What is the fastest land animal?","Jaké je nejrychlejší suchozemské zvíře?",listOf("cheetah"),listOf("gepard"),"The cheetah is the fastest land animal.","Gepard je nejrychlejší suchozemské zvíře."),
        q("nat004",2,Category.NATURE,"Which tree produces acorns?","Který strom má žaludy?",listOf("oak","oak tree"),listOf("dub"),"Oak trees produce acorns.","Žaludy rostou na dubu."),
        q("nat005",3,Category.NATURE,"What is the largest species of shark?","Jaký je největší druh žraloka?",listOf("whale shark"),listOf("zralok obrovsky","žralok obrovský"),"The whale shark is the largest shark.","Žralok obrovský je největší žralok."),
        q("nat006",3,Category.NATURE,"Which mammal is capable of true sustained flight?","Který savec je schopný skutečného aktivního letu?",listOf("bat","bats"),listOf("netopyr","netopýr"),"Bats are the only mammals capable of sustained powered flight.","Netopýři jsou jediní savci schopní trvalého aktivního letu."),
        q("nat007",4,Category.NATURE,"What process do plants use to convert light into chemical energy?","Jakým procesem rostliny mění světlo na chemickou energii?",listOf("photosynthesis"),listOf("fotosynteza","fotosyntéza"),"Plants use photosynthesis.","Rostliny používají fotosyntézu."),
        q("nat008",4,Category.NATURE,"Which animal has the highest blood pressure?","Které zvíře má nejvyšší krevní tlak?",listOf("giraffe"),listOf("zirafa","žirafa"),"Giraffes have exceptionally high blood pressure.","Žirafy mají mimořádně vysoký krevní tlak."),

        q("sport001",1,Category.SPORTS,"How many players does a soccer team have on the field at kickoff?","Kolik hráčů má fotbalový tým na hřišti při výkopu?",listOf("11","eleven"),listOf("11","jedenact","jedenáct"),"A soccer team fields eleven players.","Fotbalový tým má na hřišti jedenáct hráčů."),
        q("sport002",1,Category.SPORTS,"In tennis, what score comes after thirty?","Jaké skóre následuje v tenise po třiceti?",listOf("40","forty"),listOf("40","ctyricet","čtyřicet"),"Tennis scoring goes 15, 30, 40.","Tenisové skóre jde 15, 30, 40."),
        q("sport003",2,Category.SPORTS,"How many rings are on the Olympic symbol?","Kolik kruhů má olympijský symbol?",listOf("5","five"),listOf("5","pet","pět"),"The Olympic symbol has five rings.","Olympijský symbol má pět kruhů."),
        q("sport004",2,Category.SPORTS,"Which sport uses a shuttlecock?","Ve kterém sportu se používá košíček neboli shuttlecock?",listOf("badminton"),listOf("badminton"),"Badminton uses a shuttlecock.","Košíček se používá v badmintonu."),
        q("sport005",3,Category.SPORTS,"How long is a marathon in kilometers approximately?","Kolik kilometrů měří maraton přibližně?",listOf("42.195","42","forty two point one nine five"),listOf("42.195","42","ctyricet dva cela jedna devet pet"),"A marathon is 42.195 kilometers.","Maraton měří 42,195 kilometru."),
        q("sport006",3,Category.SPORTS,"How many points is a touchdown worth before the extra point?","Kolik bodů má touchdown před extra bodem?",listOf("6","six"),listOf("6","sest","šest"),"A touchdown is worth six points.","Touchdown má hodnotu šesti bodů."),
        q("sport007",4,Category.SPORTS,"Which country won the first FIFA World Cup in 1930?","Která země vyhrála první mistrovství světa ve fotbale v roce 1930?",listOf("uruguay"),listOf("uruguay"),"Uruguay won the first World Cup.","První mistrovství světa vyhrála Uruguay."),
        q("sport008",4,Category.SPORTS,"What is the maximum break in snooker under normal rules?","Jaký je maximální break ve snookeru za běžných pravidel?",listOf("147","one hundred forty seven"),listOf("147","sto ctyricet sedm","sto čtyřicet sedm"),"The standard maximum break is 147.","Standardní maximální break je 147."),

        q("cult001",1,Category.CULTURE,"Who wrote Romeo and Juliet?","Kdo napsal Romea a Julii?",listOf("william shakespeare","shakespeare"),listOf("william shakespeare","shakespeare"),"Shakespeare wrote Romeo and Juliet.","Romea a Julii napsal William Shakespeare."),
        q("cult002",1,Category.CULTURE,"Who painted the Mona Lisa?","Kdo namaloval Monu Lisu?",listOf("leonardo da vinci","da vinci","leonardo"),listOf("leonardo da vinci","da vinci","leonardo"),"Leonardo da Vinci painted the Mona Lisa.","Monu Lisu namaloval Leonardo da Vinci."),
        q("cult003",2,Category.CULTURE,"Which composer wrote the Fifth Symphony with the famous four-note opening?","Který skladatel napsal Pátou symfonii se známým čtyřtónovým úvodem?",listOf("beethoven","ludwig van beethoven"),listOf("beethoven","ludwig van beethoven"),"Beethoven wrote the famous Fifth Symphony.","Slavnou Pátou symfonii napsal Beethoven."),
        q("cult004",2,Category.CULTURE,"Who wrote The Hobbit?","Kdo napsal Hobita?",listOf("j r r tolkien","tolkien"),listOf("j r r tolkien","tolkien"),"J. R. R. Tolkien wrote The Hobbit.","Hobita napsal J. R. R. Tolkien."),
        q("cult005",3,Category.CULTURE,"Which artist painted The Starry Night?","Který malíř namaloval Hvězdnou noc?",listOf("vincent van gogh","van gogh"),listOf("vincent van gogh","van gogh"),"Vincent van Gogh painted The Starry Night.","Hvězdnou noc namaloval Vincent van Gogh."),
        q("cult006",3,Category.CULTURE,"Who composed The Four Seasons?","Kdo složil Čtvero ročních dob?",listOf("vivaldi","antonio vivaldi"),listOf("vivaldi","antonio vivaldi"),"Antonio Vivaldi composed The Four Seasons.","Čtvero ročních dob složil Antonio Vivaldi."),
        q("cult007",4,Category.CULTURE,"Who wrote One Hundred Years of Solitude?","Kdo napsal Sto roků samoty?",listOf("gabriel garcia marquez","garcia marquez","marquez"),listOf("gabriel garcia marquez","marquez"),"Gabriel García Márquez wrote the novel.","Román napsal Gabriel García Márquez."),
        q("cult008",4,Category.CULTURE,"Which architect designed Fallingwater?","Který architekt navrhl Fallingwater?",listOf("frank lloyd wright","wright"),listOf("frank lloyd wright","wright"),"Frank Lloyd Wright designed Fallingwater.","Fallingwater navrhl Frank Lloyd Wright."),

        q("car001",1,Category.CARS,"What does ABS stand for in a car?","Co znamená zkratka ABS u auta?",listOf("anti lock braking system","antilock braking system"),listOf("anti lock braking system","protiblokovaci system","protiblokovací systém"),"ABS helps prevent wheel lock under braking.","ABS pomáhá zabránit zablokování kol při brzdění."),
        q("car002",1,Category.CARS,"Which pedal is normally on the far left in a manual car?","Který pedál je u manuálního auta úplně vlevo?",listOf("clutch"),listOf("spojka"),"The clutch pedal is on the left.","Vlevo je spojkový pedál."),
        q("car003",2,Category.CARS,"What does EV stand for?","Co znamená zkratka EV?",listOf("electric vehicle"),listOf("electric vehicle","elektricke vozidlo","elektrické vozidlo"),"EV means electric vehicle.","EV znamená elektrické vozidlo."),
        q("car004",2,Category.CARS,"What does RPM measure in an engine?","Co u motoru měří RPM?",listOf("revolutions per minute","engine speed"),listOf("otacky za minutu","otáčky za minutu","otacky motoru"),"RPM is revolutions per minute.","RPM jsou otáčky za minutu."),
        q("car005",3,Category.CARS,"What device converts AC to DC when charging many EV batteries?","Jaké zařízení převádí střídavý proud na stejnosměrný při nabíjení mnoha elektromobilů?",listOf("onboard charger","charger","rectifier"),listOf("palubni nabijecka","palubní nabíječka","usmernovac","usměrňovač"),"The onboard charger rectifies AC into DC.","Palubní nabíječka převádí AC na DC."),
        q("car006",3,Category.CARS,"What does regenerative braking recover?","Co získává rekuperační brzdění?",listOf("energy","electrical energy","kinetic energy"),listOf("energii","elektrickou energii","kinetickou energii"),"Regenerative braking converts kinetic energy back into electrical energy.","Rekuperace mění část kinetické energie zpět na elektrickou."),
        q("car007",4,Category.CARS,"What is torque measured in under the SI system?","V čem se v soustavě SI měří točivý moment?",listOf("newton metres","newton meters","newton metre","newton meter"),listOf("newtonmetr","newtonmetry","newton metry"),"Torque is measured in newton-metres.","Točivý moment se měří v newtonmetrech."),
        q("car008",4,Category.CARS,"What does a differential allow driven wheels to do in a turn?","Co umožňuje diferenciál hnacím kolům v zatáčce?",listOf("rotate at different speeds","turn at different speeds","different speeds"),listOf("tocit se ruznou rychlosti","otáčet se různou rychlostí","rozdilne otacky"),"A differential lets wheels rotate at different speeds.","Diferenciál umožňuje kolům otáčet se různou rychlostí."),

        q("num001",1,Category.NUMBERS,"What is 7 plus 8?","Kolik je 7 plus 8?",listOf("15","fifteen"),listOf("15","patnact","patnáct"),"Seven plus eight is fifteen.","Sedm plus osm je patnáct."),
        q("num002",1,Category.NUMBERS,"What is 12 times 5?","Kolik je 12 krát 5?",listOf("60","sixty"),listOf("60","sedesat","šedesát"),"Twelve times five is sixty.","Dvanáct krát pět je šedesát."),
        q("num003",2,Category.NUMBERS,"What is 150 minus 67?","Kolik je 150 minus 67?",listOf("83","eighty three"),listOf("83","osmdesat tri","osmdesát tři"),"The answer is 83.","Výsledek je 83."),
        q("num004",2,Category.NUMBERS,"What is half of 246?","Kolik je polovina z 246?",listOf("123","one hundred twenty three","one hundred and twenty three"),listOf("123","sto dvacet tri","sto dvacet tři"),"Half of 246 is 123.","Polovina z 246 je 123."),
        q("num005",3,Category.NUMBERS,"What is 18 times 7?","Kolik je 18 krát 7?",listOf("126","one hundred twenty six"),listOf("126","sto dvacet sest","sto dvacet šest"),"Eighteen times seven is 126.","Osmnáct krát sedm je 126."),
        q("num006",3,Category.NUMBERS,"What is 640 divided by 8?","Kolik je 640 děleno 8?",listOf("80","eighty"),listOf("80","osmdesat","osmdesát"),"640 divided by 8 is 80.","640 děleno 8 je 80."),
        q("num007",4,Category.NUMBERS,"What is 13 squared?","Kolik je 13 na druhou?",listOf("169","one hundred sixty nine"),listOf("169","sto sedesat devet","sto šedesát devět"),"Thirteen squared is 169.","Třináct na druhou je 169."),
        q("num008",4,Category.NUMBERS,"What is 7 cubed?","Kolik je 7 na třetí?",listOf("343","three hundred forty three"),listOf("343","tri sta ctyricet tri","tři sta čtyřicet tři"),"Seven cubed is 343.","Sedm na třetí je 343."),
        q("num009",5,Category.NUMBERS,"What is 17 times 19?","Kolik je 17 krát 19?",listOf("323","three hundred twenty three"),listOf("323","tri sta dvacet tri","tři sta dvacet tři"),"Seventeen times nineteen is 323.","Sedmnáct krát devatenáct je 323."),
        q("num010",5,Category.NUMBERS,"What is 15 percent of 360?","Kolik je 15 procent z 360?",listOf("54","fifty four"),listOf("54","padesat ctyri","padesát čtyři"),"Fifteen percent of 360 is 54.","Patnáct procent z 360 je 54.")
    ) + generatedCapitalQuestions() + generatedElementQuestions() + generatedNumberQuestions() +
        generatedMovieQuestions() + generatedHistoryExpansion() + generatedSportsExpansion()


    private data class CapitalFact(
        val countryEn: String,
        val countryCs: String,
        val capital: String,
        val difficulty: Int
    )

    private fun generatedCapitalQuestions(): List<LocalizedQuestion> {
        val facts = listOf(
            CapitalFact("Belgium","Belgie","Brussels",2),
            CapitalFact("Austria","Rakousko","Vienna",1),
            CapitalFact("Poland","Polsko","Warsaw",1),
            CapitalFact("Hungary","Maďarsko","Budapest",1),
            CapitalFact("Slovakia","Slovensko","Bratislava",1),
            CapitalFact("Slovenia","Slovinsko","Ljubljana",2),
            CapitalFact("Croatia","Chorvatsko","Zagreb",2),
            CapitalFact("Serbia","Srbsko","Belgrade",2),
            CapitalFact("Romania","Rumunsko","Bucharest",2),
            CapitalFact("Bulgaria","Bulharsko","Sofia",2),
            CapitalFact("Greece","Řecko","Athens",1),
            CapitalFact("Ireland","Irsko","Dublin",1),
            CapitalFact("Iceland","Island","Reykjavik",2),
            CapitalFact("Finland","Finsko","Helsinki",2),
            CapitalFact("Sweden","Švédsko","Stockholm",1),
            CapitalFact("Denmark","Dánsko","Copenhagen",1),
            CapitalFact("Switzerland","Švýcarsko","Bern",2),
            CapitalFact("Ukraine","Ukrajina","Kyiv",1),
            CapitalFact("Lithuania","Litva","Vilnius",3),
            CapitalFact("Latvia","Lotyšsko","Riga",2),
            CapitalFact("Estonia","Estonsko","Tallinn",2),
            CapitalFact("Turkey","Turecko","Ankara",2),
            CapitalFact("Japan","Japonsko","Tokyo",1),
            CapitalFact("China","Čína","Beijing",1),
            CapitalFact("South Korea","Jižní Korea","Seoul",1),
            CapitalFact("India","Indie","New Delhi",1),
            CapitalFact("Thailand","Thajsko","Bangkok",2),
            CapitalFact("Vietnam","Vietnam","Hanoi",2),
            CapitalFact("Indonesia","Indonésie","Jakarta",2),
            CapitalFact("Malaysia","Malajsie","Kuala Lumpur",2),
            CapitalFact("Philippines","Filipíny","Manila",2),
            CapitalFact("Singapore","Singapur","Singapore",1),
            CapitalFact("Pakistan","Pákistán","Islamabad",3),
            CapitalFact("Bangladesh","Bangladéš","Dhaka",3),
            CapitalFact("Nepal","Nepál","Kathmandu",3),
            CapitalFact("Mongolia","Mongolsko","Ulaanbaatar",4),
            CapitalFact("Iran","Írán","Tehran",2),
            CapitalFact("Iraq","Irák","Baghdad",2),
            CapitalFact("Saudi Arabia","Saúdská Arábie","Riyadh",2),
            CapitalFact("United Arab Emirates","Spojené arabské emiráty","Abu Dhabi",2),
            CapitalFact("Egypt","Egypt","Cairo",1),
            CapitalFact("Morocco","Maroko","Rabat",3),
            CapitalFact("Algeria","Alžírsko","Algiers",3),
            CapitalFact("Tunisia","Tunisko","Tunis",2),
            CapitalFact("Kenya","Keňa","Nairobi",2),
            CapitalFact("Nigeria","Nigérie","Abuja",3),
            CapitalFact("Ghana","Ghana","Accra",3),
            CapitalFact("South Africa","Jihoafrická republika","Pretoria",3),
            CapitalFact("Argentina","Argentina","Buenos Aires",1),
            CapitalFact("Brazil","Brazílie","Brasilia",2),
            CapitalFact("Chile","Chile","Santiago",2),
            CapitalFact("Peru","Peru","Lima",2),
            CapitalFact("Colombia","Kolumbie","Bogota",2),
            CapitalFact("Mexico","Mexiko","Mexico City",1),
            CapitalFact("Cuba","Kuba","Havana",2),
            CapitalFact("United States","Spojené státy","Washington",1),
            CapitalFact("Jamaica","Jamajka","Kingston",3),
            CapitalFact("Panama","Panama","Panama City",2),
            CapitalFact("Costa Rica","Kostarika","San Jose",3),
            CapitalFact("Uruguay","Uruguay","Montevideo",3)
        )

        val forward = facts.mapIndexed { index, fact ->
            LocalizedQuestion(
                id = "cap" + (index + 1).toString().padStart(3, '0'),
                difficulty = fact.difficulty,
                category = Category.GEOGRAPHY,
                promptEn = "What is the capital of " + fact.countryEn + "?",
                promptCs = "Jaké je hlavní město země " + fact.countryCs + "?",
                answersEn = listOf(fact.capital.lowercase()),
                answersCs = listOf(fact.capital.lowercase()),
                explanationEn = fact.capital + " is the capital of " + fact.countryEn + ".",
                explanationCs = fact.capital + " je hlavní město země " + fact.countryCs + "."
            )
        }

        val reverse = facts.mapIndexed { index, fact ->
            LocalizedQuestion(
                id = "country" + (index + 1).toString().padStart(3, '0'),
                difficulty = minOf(5, fact.difficulty + 1),
                category = Category.GEOGRAPHY,
                promptEn = "Which country has " + fact.capital + " as its capital?",
                promptCs = "Která země má hlavní město " + fact.capital + "?",
                answersEn = listOf(fact.countryEn.lowercase()),
                answersCs = listOf(fact.countryCs.lowercase()),
                explanationEn = fact.capital + " is the capital of " + fact.countryEn + ".",
                explanationCs = fact.capital + " je hlavní město země " + fact.countryCs + "."
            )
        }

        return forward + reverse
    }

    private data class ElementFact(
        val nameEn: String,
        val nameCs: String,
        val symbol: String,
        val difficulty: Int
    )

    private fun generatedElementQuestions(): List<LocalizedQuestion> {
        val facts = listOf(
            ElementFact("hydrogen","vodík","H",1),
            ElementFact("helium","helium","He",1),
            ElementFact("lithium","lithium","Li",2),
            ElementFact("beryllium","beryllium","Be",3),
            ElementFact("boron","bor","B",2),
            ElementFact("carbon","uhlík","C",1),
            ElementFact("nitrogen","dusík","N",1),
            ElementFact("oxygen","kyslík","O",1),
            ElementFact("fluorine","fluor","F",2),
            ElementFact("neon","neon","Ne",2),
            ElementFact("sodium","sodík","Na",2),
            ElementFact("magnesium","hořčík","Mg",2),
            ElementFact("aluminium","hliník","Al",2),
            ElementFact("silicon","křemík","Si",3),
            ElementFact("phosphorus","fosfor","P",2),
            ElementFact("sulfur","síra","S",2),
            ElementFact("chlorine","chlor","Cl",2),
            ElementFact("argon","argon","Ar",3),
            ElementFact("potassium","draslík","K",2),
            ElementFact("calcium","vápník","Ca",2),
            ElementFact("iron","železo","Fe",1),
            ElementFact("cobalt","kobalt","Co",3),
            ElementFact("nickel","nikl","Ni",3),
            ElementFact("copper","měď","Cu",2),
            ElementFact("zinc","zinek","Zn",2),
            ElementFact("silver","stříbro","Ag",2),
            ElementFact("tin","cín","Sn",3),
            ElementFact("iodine","jód","I",2),
            ElementFact("tungsten","wolfram","W",4),
            ElementFact("platinum","platina","Pt",3),
            ElementFact("gold","zlato","Au",1),
            ElementFact("mercury","rtuť","Hg",3),
            ElementFact("lead","olovo","Pb",3),
            ElementFact("uranium","uran","U",2),
            ElementFact("plutonium","plutonium","Pu",3)
        )

        val forward = facts.mapIndexed { index, fact ->
            LocalizedQuestion(
                id = "elm" + (index + 1).toString().padStart(3, '0'),
                difficulty = fact.difficulty,
                category = Category.SCIENCE,
                promptEn = "What is the chemical symbol for " + fact.nameEn + "?",
                promptCs = "Jaká je chemická značka prvku " + fact.nameCs + "?",
                answersEn = listOf(fact.symbol.lowercase()),
                answersCs = listOf(fact.symbol.lowercase()),
                explanationEn = fact.nameEn.replaceFirstChar { it.uppercase() } +
                    " has the symbol " + fact.symbol + ".",
                explanationCs = fact.nameCs.replaceFirstChar { it.uppercase() } +
                    " má značku " + fact.symbol + "."
            )
        }

        val reverse = facts.mapIndexed { index, fact ->
            LocalizedQuestion(
                id = "elmname" + (index + 1).toString().padStart(3, '0'),
                difficulty = minOf(5, fact.difficulty + 1),
                category = Category.SCIENCE,
                promptEn = "Which element has the symbol " + fact.symbol + "?",
                promptCs = "Který prvek má značku " + fact.symbol + "?",
                answersEn = listOf(fact.nameEn.lowercase()),
                answersCs = listOf(fact.nameCs.lowercase()),
                explanationEn = fact.symbol + " is the symbol for " + fact.nameEn + ".",
                explanationCs = fact.symbol + " je značka prvku " + fact.nameCs + "."
            )
        }

        return forward + reverse
    }

    private fun generatedNumberQuestions(): List<LocalizedQuestion> {
        val result = mutableListOf<LocalizedQuestion>()

        for (i in 1..200) {
            val a = 10 + i * 2
            val b = 3 + (i % 17)
            val value = a + b
            result += LocalizedQuestion(
                id = "add" + i.toString().padStart(3, '0'),
                difficulty = when {
                    value < 50 -> 1
                    value < 150 -> 2
                    value < 300 -> 3
                    else -> 4
                },
                category = Category.NUMBERS,
                promptEn = "What is " + a + " plus " + b + "?",
                promptCs = "Kolik je " + a + " plus " + b + "?",
                answersEn = listOf(value.toString()),
                answersCs = listOf(value.toString()),
                explanationEn = a.toString() + " plus " + b + " is " + value + ".",
                explanationCs = a.toString() + " plus " + b + " je " + value + "."
            )
        }

        for (i in 1..200) {
            val a = 90 + i * 3
            val b = 7 + (i % 29)
            val value = a - b
            result += LocalizedQuestion(
                id = "sub" + i.toString().padStart(3, '0'),
                difficulty = when {
                    a < 150 -> 2
                    a < 350 -> 3
                    else -> 4
                },
                category = Category.NUMBERS,
                promptEn = "What is " + a + " minus " + b + "?",
                promptCs = "Kolik je " + a + " minus " + b + "?",
                answersEn = listOf(value.toString()),
                answersCs = listOf(value.toString()),
                explanationEn = a.toString() + " minus " + b + " is " + value + ".",
                explanationCs = a.toString() + " minus " + b + " je " + value + "."
            )
        }

        for (i in 0 until 200) {
            val a = 2 + i / 10
            val b = 2 + i % 10
            val value = a * b
            result += LocalizedQuestion(
                id = "mul" + (i + 1).toString().padStart(3, '0'),
                difficulty = when {
                    value < 40 -> 1
                    value < 100 -> 2
                    value < 180 -> 3
                    else -> 4
                },
                category = Category.NUMBERS,
                promptEn = "What is " + a + " times " + b + "?",
                promptCs = "Kolik je " + a + " krát " + b + "?",
                answersEn = listOf(value.toString()),
                answersCs = listOf(value.toString()),
                explanationEn = a.toString() + " times " + b + " is " + value + ".",
                explanationCs = a.toString() + " krát " + b + " je " + value + "."
            )
        }

        for (i in 1..120) {
            val divisor = 2 + (i % 11)
            val answer = 3 + i
            val dividend = divisor * answer
            result += LocalizedQuestion(
                id = "div" + i.toString().padStart(3, '0'),
                difficulty = when {
                    answer < 20 -> 2
                    answer < 60 -> 3
                    else -> 4
                },
                category = Category.NUMBERS,
                promptEn = "What is " + dividend + " divided by " + divisor + "?",
                promptCs = "Kolik je " + dividend + " děleno " + divisor + "?",
                answersEn = listOf(answer.toString()),
                answersCs = listOf(answer.toString()),
                explanationEn = dividend.toString() + " divided by " + divisor + " is " + answer + ".",
                explanationCs = dividend.toString() + " děleno " + divisor + " je " + answer + "."
            )
        }

        for (n in 2..51) {
            val value = n * n
            result += LocalizedQuestion(
                id = "sq" + n.toString().padStart(3, '0'),
                difficulty = when {
                    n <= 12 -> 2
                    n <= 25 -> 3
                    n <= 40 -> 4
                    else -> 5
                },
                category = Category.NUMBERS,
                promptEn = "What is " + n + " squared?",
                promptCs = "Kolik je " + n + " na druhou?",
                answersEn = listOf(value.toString()),
                answersCs = listOf(value.toString()),
                explanationEn = n.toString() + " squared is " + value + ".",
                explanationCs = n.toString() + " na druhou je " + value + "."
            )
        }

        return result
    }

    private data class MovieFact(
        val film: String,
        val director: String,
        val year: Int,
        val difficulty: Int
    )

    private fun generatedMovieQuestions(): List<LocalizedQuestion> {
        val facts = listOf(
            MovieFact("Jaws","Steven Spielberg",1975,2),
            MovieFact("E.T. the Extra-Terrestrial","Steven Spielberg",1982,2),
            MovieFact("Jurassic Park","Steven Spielberg",1993,1),
            MovieFact("Schindler's List","Steven Spielberg",1993,3),
            MovieFact("Titanic","James Cameron",1997,1),
            MovieFact("Avatar","James Cameron",2009,1),
            MovieFact("The Terminator","James Cameron",1984,2),
            MovieFact("Aliens","James Cameron",1986,3),
            MovieFact("Pulp Fiction","Quentin Tarantino",1994,2),
            MovieFact("Kill Bill: Volume 1","Quentin Tarantino",2003,3),
            MovieFact("Inception","Christopher Nolan",2010,1),
            MovieFact("Interstellar","Christopher Nolan",2014,1),
            MovieFact("The Dark Knight","Christopher Nolan",2008,1),
            MovieFact("Dunkirk","Christopher Nolan",2017,3),
            MovieFact("The Godfather","Francis Ford Coppola",1972,1),
            MovieFact("Apocalypse Now","Francis Ford Coppola",1979,3),
            MovieFact("Goodfellas","Martin Scorsese",1990,2),
            MovieFact("Taxi Driver","Martin Scorsese",1976,3),
            MovieFact("The Departed","Martin Scorsese",2006,2),
            MovieFact("The Wolf of Wall Street","Martin Scorsese",2013,1),
            MovieFact("Alien","Ridley Scott",1979,2),
            MovieFact("Blade Runner","Ridley Scott",1982,2),
            MovieFact("Gladiator","Ridley Scott",2000,1),
            MovieFact("The Martian","Ridley Scott",2015,2),
            MovieFact("Fight Club","David Fincher",1999,2),
            MovieFact("Se7en","David Fincher",1995,3),
            MovieFact("The Social Network","David Fincher",2010,2),
            MovieFact("Forrest Gump","Robert Zemeckis",1994,1),
            MovieFact("Back to the Future","Robert Zemeckis",1985,1),
            MovieFact("Cast Away","Robert Zemeckis",2000,2)
        )

        return facts.flatMapIndexed { index, fact ->
            listOf(
                LocalizedQuestion(
                    id = "movdir" + (index + 1).toString().padStart(3, '0'),
                    difficulty = fact.difficulty,
                    category = Category.MOVIES,
                    promptEn = "Who directed " + fact.film + "?",
                    promptCs = "Kdo režíroval film " + fact.film + "?",
                    answersEn = listOf(fact.director.lowercase()),
                    answersCs = listOf(fact.director.lowercase()),
                    explanationEn = fact.film + " was directed by " + fact.director + ".",
                    explanationCs = "Film " + fact.film + " režíroval " + fact.director + "."
                ),
                LocalizedQuestion(
                    id = "movyr" + (index + 1).toString().padStart(3, '0'),
                    difficulty = minOf(5, fact.difficulty + 1),
                    category = Category.MOVIES,
                    promptEn = "In what year was " + fact.film + " released?",
                    promptCs = "V kterém roce měl premiéru film " + fact.film + "?",
                    answersEn = listOf(fact.year.toString()),
                    answersCs = listOf(fact.year.toString()),
                    explanationEn = fact.film + " was released in " + fact.year + ".",
                    explanationCs = "Film " + fact.film + " měl premiéru v roce " + fact.year + "."
                )
            )
        }
    }

    private data class HistoryFact(
        val eventEn: String,
        val eventCs: String,
        val year: Int,
        val difficulty: Int
    )

    private fun generatedHistoryExpansion(): List<LocalizedQuestion> {
        val facts = listOf(
            HistoryFact("the fall of the Western Roman Empire","pád Západořímské říše",476,4),
            HistoryFact("the Battle of Hastings","bitva u Hastingsu",1066,3),
            HistoryFact("the signing of Magna Carta","podepsání Magny Charty",1215,4),
            HistoryFact("the fall of Constantinople","pád Konstantinopole",1453,4),
            HistoryFact("Columbus's first voyage to the Americas","první Kolumbova výprava do Ameriky",1492,2),
            HistoryFact("the start of the Protestant Reformation","začátek protestantské reformace",1517,4),
            HistoryFact("the defeat of the Spanish Armada","porážka španělské Armady",1588,4),
            HistoryFact("the English Civil War began","začátek anglické občanské války",1642,5),
            HistoryFact("the American Declaration of Independence","americká Deklarace nezávislosti",1776,1),
            HistoryFact("the start of the French Revolution","začátek Francouzské revoluce",1789,1),
            HistoryFact("the Battle of Waterloo","bitva u Waterloo",1815,2),
            HistoryFact("the publication of The Communist Manifesto","vydání Komunistického manifestu",1848,4),
            HistoryFact("the start of the American Civil War","začátek americké občanské války",1861,2),
            HistoryFact("the unification of Germany","sjednocení Německa",1871,4),
            HistoryFact("the first modern Olympic Games","první novodobé olympijské hry",1896,2),
            HistoryFact("the sinking of the Titanic","potopení Titaniku",1912,1),
            HistoryFact("the start of World War One","začátek první světové války",1914,1),
            HistoryFact("the Russian Revolution","ruská revoluce",1917,2),
            HistoryFact("the end of World War One","konec první světové války",1918,1),
            HistoryFact("the Wall Street Crash","krach na Wall Street",1929,2),
            HistoryFact("the start of World War Two","začátek druhé světové války",1939,1),
            HistoryFact("D-Day landings in Normandy","vylodění v Normandii",1944,2),
            HistoryFact("the end of World War Two in Europe","konec druhé světové války v Evropě",1945,1),
            HistoryFact("the founding of NATO","založení NATO",1949,3),
            HistoryFact("the first human spaceflight by Yuri Gagarin","první let člověka do vesmíru Jurije Gagarina",1961,2),
            HistoryFact("the Apollo 11 Moon landing","přistání Apolla 11 na Měsíci",1969,1),
            HistoryFact("the fall of Saigon","pád Saigonu",1975,3),
            HistoryFact("the fall of the Berlin Wall","pád Berlínské zdi",1989,1),
            HistoryFact("the dissolution of the Soviet Union","rozpad Sovětského svazu",1991,2),
            HistoryFact("the creation of the Czech Republic","vznik České republiky",1993,1)
        )

        return facts.flatMapIndexed { index, fact ->
            listOf(
                LocalizedQuestion(
                    id = "histyrx" + (index + 1).toString().padStart(3, '0'),
                    difficulty = fact.difficulty,
                    category = Category.HISTORY,
                    promptEn = "In what year did " + fact.eventEn + " occur?",
                    promptCs = "V kterém roce nastal " + fact.eventCs + "?",
                    answersEn = listOf(fact.year.toString()),
                    answersCs = listOf(fact.year.toString()),
                    explanationEn = fact.eventEn.replaceFirstChar { it.uppercase() } +
                        " occurred in " + fact.year + ".",
                    explanationCs = fact.eventCs.replaceFirstChar { it.uppercase() } +
                        " nastal v roce " + fact.year + "."
                ),
                LocalizedQuestion(
                    id = "histdec" + (index + 1).toString().padStart(3, '0'),
                    difficulty = minOf(5, fact.difficulty + 1),
                    category = Category.HISTORY,
                    promptEn = "Which decade includes the year " + fact.year + "?",
                    promptCs = "Do kterého desetiletí patří rok " + fact.year + "?",
                    answersEn = listOf(((fact.year / 10) * 10).toString() + "s"),
                    answersCs = listOf(((fact.year / 10) * 10).toString()),
                    explanationEn = fact.year.toString() + " is in the " +
                        ((fact.year / 10) * 10) + "s.",
                    explanationCs = "Rok " + fact.year + " patří do " +
                        ((fact.year / 10) * 10) + ". let."
                )
            )
        }
    }

    private data class SportsFact(
        val questionEn: String,
        val questionCs: String,
        val answerEn: List<String>,
        val answerCs: List<String>,
        val explanationEn: String,
        val explanationCs: String,
        val difficulty: Int
    )

    private fun generatedSportsExpansion(): List<LocalizedQuestion> {
        val facts = listOf(
            SportsFact("How many players does a soccer team have on the field at the start of a match?","Kolik hráčů má fotbalový tým na hřišti na začátku zápasu?",listOf("11","eleven"),listOf("11","jedenact","jedenáct"),"A soccer team starts with eleven players.","Fotbalový tým začíná s jedenácti hráči.",1),
            SportsFact("How many points is a touchdown worth in American football before the extra point?","Kolik bodů má touchdown v americkém fotbalu před extra bodem?",listOf("6","six"),listOf("6","sest","šest"),"A touchdown is worth six points.","Touchdown má hodnotu šest bodů.",2),
            SportsFact("How many players are on court for one basketball team?","Kolik hráčů jednoho basketbalového týmu je současně na hřišti?",listOf("5","five"),listOf("5","pet","pět"),"Five players per team are on court.","Na hřišti je pět hráčů jednoho týmu.",1),
            SportsFact("How many sets must a men's Grand Slam tennis player win to win a match?","Kolik setů musí muž vyhrát v grandslamovém tenisu, aby vyhrál zápas?",listOf("3","three"),listOf("3","tri","tři"),"Men's Grand Slam singles are best of five sets.","Mužský grandslam se hraje na tři vítězné sety.",3),
            SportsFact("What surface is Wimbledon traditionally played on?","Na jakém povrchu se tradičně hraje Wimbledon?",listOf("grass"),listOf("trava","tráva"),"Wimbledon is played on grass.","Wimbledon se hraje na trávě.",1),
            SportsFact("How many rings are on the Olympic symbol?","Kolik kruhů má olympijský symbol?",listOf("5","five"),listOf("5","pet","pět"),"The Olympic symbol has five rings.","Olympijský symbol má pět kruhů.",1),
            SportsFact("In which sport is the Stanley Cup awarded?","Ve kterém sportu se uděluje Stanley Cup?",listOf("ice hockey","hockey"),listOf("ledni hokej","hokej"),"The Stanley Cup is awarded in ice hockey.","Stanley Cup se uděluje v ledním hokeji.",1),
            SportsFact("In which sport would you perform a slam dunk?","Ve kterém sportu se provádí slam dunk?",listOf("basketball"),listOf("basketbal"),"A slam dunk is a basketball shot.","Slam dunk je basketbalové zakončení.",1),
            SportsFact("How many bases are there in baseball?","Kolik met je v baseballu?",listOf("4","four"),listOf("4","ctyri","čtyři"),"Baseball has four bases including home plate.","Baseball má čtyři mety včetně domácí mety.",2),
            SportsFact("What is the maximum score with one dart in standard darts?","Jaké je nejvyšší skóre jednou šipkou v klasických šipkách?",listOf("60","sixty"),listOf("60","sedesat","šedesát"),"Triple 20 scores sixty.","Trojitá dvacítka dává šedesát bodů.",2),
            SportsFact("How long is an Olympic swimming pool?","Jak dlouhý je olympijský plavecký bazén?",listOf("50 meters","50","fifty meters"),listOf("50 metru","50","padesat metru"),"An Olympic pool is fifty meters long.","Olympijský bazén je dlouhý padesát metrů.",2),
            SportsFact("How many holes are played in a standard round of golf?","Kolik jamek má standardní golfové kolo?",listOf("18","eighteen"),listOf("18","osmnact","osmnáct"),"A standard golf round has eighteen holes.","Standardní golfové kolo má osmnáct jamek.",1),
            SportsFact("What color jersey is worn by the Tour de France overall leader?","Jakou barvu dresu nosí průběžný lídr Tour de France?",listOf("yellow"),listOf("zluty","žlutý"),"The overall leader wears the yellow jersey.","Průběžný lídr nosí žlutý trikot.",2),
            SportsFact("In Formula One, what flag signals the end of a race?","Jaká vlajka ve Formuli 1 signalizuje konec závodu?",listOf("checkered flag","chequered flag"),listOf("sachovnicova vlajka","šachovnicová vlajka"),"The chequered flag signals the finish.","Konec závodu signalizuje šachovnicová vlajka.",1),
            SportsFact("What is the official marathon distance in kilometers?","Jaká je oficiální délka maratonu v kilometrech?",listOf("42.195","42.195 kilometers"),listOf("42.195","42,195"),"A marathon is 42.195 kilometers.","Maraton měří 42,195 kilometru.",3),
            SportsFact("How many points is a free throw worth in basketball?","Kolik bodů má trestný hod v basketbalu?",listOf("1","one"),listOf("1","jeden"),"A free throw is worth one point.","Trestný hod má hodnotu jednoho bodu.",1),
            SportsFact("How many periods are played in regulation ice hockey?","Kolik třetin má běžný hokejový zápas?",listOf("3","three"),listOf("3","tri","tři"),"Ice hockey regulation has three periods.","Hokejový zápas má tři třetiny.",1),
            SportsFact("Which sport uses the terms birdie and eagle?","Ve kterém sportu se používají pojmy birdie a eagle?",listOf("golf"),listOf("golf"),"Birdie and eagle are golf scoring terms.","Birdie a eagle jsou golfové pojmy.",1),
            SportsFact("Which sport uses a pommel horse?","Ve kterém sportu se používá kůň našíř?",listOf("gymnastics"),listOf("gymnastika"),"The pommel horse is an artistic gymnastics apparatus.","Kůň našíř je nářadí sportovní gymnastiky.",2),
            SportsFact("How many players are on court for one volleyball team?","Kolik hráčů jednoho volejbalového týmu je na hřišti?",listOf("6","six"),listOf("6","sest","šest"),"Six players per team are on court.","Na hřišti je šest hráčů jednoho týmu.",1),
            SportsFact("What is zero called in tennis scoring?","Jak se v tenisovém skóre říká nule?",listOf("love"),listOf("love"),"Zero in tennis scoring is called love.","Nula se v tenisovém skóre označuje jako love.",2),
            SportsFact("In boxing, what does KO stand for?","Co znamená zkratka KO v boxu?",listOf("knockout"),listOf("knockout","knokaut"),"KO stands for knockout.","KO znamená knockout.",1),
            SportsFact("How many minutes are in a standard soccer match excluding added time?","Kolik minut má standardní fotbalový zápas bez nastavení?",listOf("90","ninety"),listOf("90","devadesat","devadesát"),"A standard match is ninety minutes.","Standardní zápas má devadesát minut.",1),
            SportsFact("Which sport features the Ryder Cup?","Ve kterém sportu se hraje Ryder Cup?",listOf("golf"),listOf("golf"),"The Ryder Cup is a golf competition.","Ryder Cup je golfová soutěž.",2),
            SportsFact("Which sport is played at Roland Garros?","Který sport se hraje na Roland Garros?",listOf("tennis"),listOf("tenis"),"Roland Garros is a Grand Slam tennis tournament.","Roland Garros je grandslamový tenisový turnaj.",1),
            SportsFact("What color card sends a player off in soccer?","Jaká karta ve fotbale znamená vyloučení hráče?",listOf("red","red card"),listOf("cervena","červená","cervena karta"),"A red card sends a player off.","Červená karta znamená vyloučení.",1),
            SportsFact("How many lanes are typically used in an Olympic 400-meter track?","Kolik drah má běžně olympijský atletický ovál?",listOf("8","eight"),listOf("8","osm"),"Major championship tracks commonly use eight lanes.","Na vrcholných soutěžích se běžně používá osm drah.",3),
            SportsFact("Which sport has positions called scrum-half and fly-half?","Ve kterém sportu jsou pozice scrum-half a fly-half?",listOf("rugby union","rugby"),listOf("ragby","rugby"),"Those are rugby union positions.","Jsou to pozice v ragby.",3),
            SportsFact("How many points is a try worth in rugby union?","Kolik bodů má položení pětky v ragby union?",listOf("5","five"),listOf("5","pet","pět"),"A try is worth five points.","Položení má hodnotu pěti bodů.",3),
            SportsFact("Which sport uses a shuttlecock?","Ve kterém sportu se používá košíček?",listOf("badminton"),listOf("badminton"),"Badminton uses a shuttlecock.","Badminton používá košíček.",1)
        )

        return facts.mapIndexed { index, fact ->
            LocalizedQuestion(
                id = "sportx" + (index + 1).toString().padStart(3, '0'),
                difficulty = fact.difficulty,
                category = Category.SPORTS,
                promptEn = fact.questionEn,
                promptCs = fact.questionCs,
                answersEn = fact.answerEn,
                answersCs = fact.answerCs,
                explanationEn = fact.explanationEn,
                explanationCs = fact.explanationCs
            )
        }
    }

    private fun q(
        id: String,
        difficulty: Int,
        category: Category,
        promptEn: String,
        promptCs: String,
        answersEn: List<String>,
        answersCs: List<String>,
        explanationEn: String,
        explanationCs: String
    ) = LocalizedQuestion(
        id,
        difficulty,
        category,
        promptEn,
        promptCs,
        answersEn,
        answersCs,
        explanationEn,
        explanationCs
    )
}
