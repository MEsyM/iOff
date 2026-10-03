package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GuessWhoEngine(context: Context) {

    init {
        LiveContentRepository.initialize(context)
    }

    enum class Region { CZECH, WORLD }

    data class Person(
        val id: String,
        val name: String,
        val aliases: List<String>,
        val region: Region,
        val difficulty: Int,
        val cluesEn: List<String>,
        val cluesCs: List<String>
    )

    data class Profile(
        val level: Int,
        val xp: Int,
        val streak: Int,
        val bestStreak: Int,
        val totalAnswered: Int,
        val totalCorrect: Int,
        val roundAnswered: Int,
        val roundCorrect: Int
    )

    enum class GuessOutcome {
        CORRECT,
        WRONG_CONTINUE,
        REVEALED
    }

    data class GuessResult(
        val outcome: GuessOutcome,
        val personName: String,
        val xpEarned: Int,
        val level: Int,
        val promoted: Boolean,
        val streak: Int,
        val hintNumber: Int,
        val roundAnswered: Int,
        val roundCorrect: Int,
        val roundFinished: Boolean
    )

    data class HintResult(
        val clue: String,
        val hintNumber: Int,
        val revealed: Boolean,
        val personName: String?,
        val roundFinished: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var currentPersonId: String? = prefs.getString(KEY_CURRENT_PERSON, null)
    private var hintIndex = prefs.getInt(KEY_HINT_INDEX, 0)
    private var sessionStreak = prefs.getInt(KEY_STREAK, 0)
    private var roundAnswered = prefs.getInt(KEY_ROUND_ANSWERED, 0)
    private var roundCorrect = prefs.getInt(KEY_ROUND_CORRECT, 0)

    fun profile(): Profile {
        val xp = prefs.getInt(KEY_XP, 0)
        return Profile(
            level = levelForXp(xp),
            xp = xp,
            streak = sessionStreak,
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            totalAnswered = prefs.getInt(KEY_TOTAL_ANSWERED, 0),
            totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0),
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect
        )
    }

    fun currentPerson(): Person? =
        currentPersonId?.let { id -> people.firstOrNull { it.id == id } }

    fun currentHintNumber(): Int = hintIndex + 1

    fun startOrResume(): Person =
        currentPerson() ?: nextPerson()

    fun currentClue(language: TriviaGameEngine.Language): String {
        val person = startOrResume()
        val clues = if (language == TriviaGameEngine.Language.CS) {
            person.cluesCs
        } else {
            person.cluesEn
        }
        return clues[hintIndex.coerceIn(0, clues.lastIndex)]
    }

    fun nextHint(language: TriviaGameEngine.Language): HintResult {
        val person = startOrResume()
        val clues = if (language == TriviaGameEngine.Language.CS) {
            person.cluesCs
        } else {
            person.cluesEn
        }

        if (hintIndex < clues.lastIndex) {
            hintIndex += 1
            prefs.edit().putInt(KEY_HINT_INDEX, hintIndex).apply()
            return HintResult(
                clue = clues[hintIndex],
                hintNumber = hintIndex + 1,
                revealed = false,
                personName = null,
                roundFinished = false
            )
        }

        val finished = registerMiss()
        return HintResult(
            clue = "",
            hintNumber = hintIndex + 1,
            revealed = true,
            personName = person.name,
            roundFinished = finished
        )
    }

    fun evaluateGuess(
        candidates: List<String>,
        language: TriviaGameEngine.Language
    ): GuessResult {
        val person = startOrResume()
        val before = profile()
        val accepted = (listOf(person.name) + person.aliases)
            .map { normalize(it, language) }

        val isCorrect = candidates.any { candidate ->
            val actual = normalize(candidate, language)
            accepted.any { expected -> nameMatches(actual, expected) }
        }

        if (isCorrect) {
            val newStreak = sessionStreak + 1
            sessionStreak = newStreak

            val baseXp = when (hintIndex) {
                0 -> 30
                1 -> 20
                else -> 12
            }
            val streakBonus = when {
                newStreak >= 10 -> 20
                newStreak >= 5 -> 10
                newStreak >= 3 -> 5
                else -> 0
            }
            val earned = baseXp + streakBonus
            val newXp = before.xp + earned
            val newLevel = levelForXp(newXp)

            roundAnswered += 1
            roundCorrect += 1

            prefs.edit()
                .putInt(KEY_XP, newXp)
                .putInt(KEY_STREAK, newStreak)
                .putInt(KEY_BEST_STREAK, max(before.bestStreak, newStreak))
                .putInt(KEY_TOTAL_ANSWERED, before.totalAnswered + 1)
                .putInt(KEY_TOTAL_CORRECT, before.totalCorrect + 1)
                .putInt(KEY_ROUND_ANSWERED, roundAnswered)
                .putInt(KEY_ROUND_CORRECT, roundCorrect)
                .putInt(seenKey(person.id), seenCount(person.id) + 1)
                .putInt(correctKey(person.id), correctCount(person.id) + 1)
                .remove(KEY_CURRENT_PERSON)
                .putInt(KEY_HINT_INDEX, 0)
                .apply()

            currentPersonId = null
            hintIndex = 0

            return GuessResult(
                outcome = GuessOutcome.CORRECT,
                personName = person.name,
                xpEarned = earned,
                level = newLevel,
                promoted = newLevel > before.level,
                streak = newStreak,
                hintNumber = currentHintNumber(),
                roundAnswered = roundAnswered,
                roundCorrect = roundCorrect,
                roundFinished = roundAnswered >= ROUND_SIZE
            )
        }

        if (hintIndex >= MAX_HINT_INDEX) {
            val finished = registerMiss()
            return GuessResult(
                outcome = GuessOutcome.REVEALED,
                personName = person.name,
                xpEarned = 0,
                level = before.level,
                promoted = false,
                streak = 0,
                hintNumber = MAX_HINT_INDEX + 1,
                roundAnswered = roundAnswered,
                roundCorrect = roundCorrect,
                roundFinished = finished
            )
        }

        return GuessResult(
            outcome = GuessOutcome.WRONG_CONTINUE,
            personName = person.name,
            xpEarned = 0,
            level = before.level,
            promoted = false,
            streak = sessionStreak,
            hintNumber = hintIndex + 1,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect,
            roundFinished = false
        )
    }

    fun skipCurrent(): GuessResult {
        val person = startOrResume()
        val before = profile()
        val finished = registerMiss()
        return GuessResult(
            outcome = GuessOutcome.REVEALED,
            personName = person.name,
            xpEarned = 0,
            level = before.level,
            promoted = false,
            streak = 0,
            hintNumber = hintIndex + 1,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect,
            roundFinished = finished
        )
    }

    fun nextPerson(): Person {
        if (roundAnswered >= ROUND_SIZE) {
            resetRound()
        }

        val p = profile()
        val maxDifficulty = min(5, p.level + 1)
        val recent = prefs.getString(KEY_RECENT, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val eligible = people.filter { it.difficulty <= maxDifficulty }

        val selected = eligible
            .filterNot { recent.contains(it.id) }
            .minByOrNull { person ->
                seenCount(person.id) * 12 +
                    correctCount(person.id) * 4 +
                    abs(person.difficulty - p.level)
            }
            ?: eligible.minByOrNull { seenCount(it.id) }
            ?: people.first()

        currentPersonId = selected.id
        hintIndex = 0

        val updatedRecent = (recent + selected.id).takeLast(10)
        prefs.edit()
            .putString(KEY_CURRENT_PERSON, selected.id)
            .putInt(KEY_HINT_INDEX, 0)
            .putString(KEY_RECENT, updatedRecent.joinToString(","))
            .apply()

        return selected
    }

    fun scoreSummary(language: TriviaGameEngine.Language): String {
        val p = profile()
        val accuracy = if (p.totalAnswered == 0) 0 else p.totalCorrect * 100 / p.totalAnswered
        return if (language == TriviaGameEngine.Language.CS) {
            "Guess Who. " + p.totalCorrect + " správně z " + p.totalAnswered +
                ". Úspěšnost " + accuracy + " procent. Aktuální série " + p.streak + "."
        } else {
            "Guess Who. " + p.totalCorrect + " correct out of " + p.totalAnswered +
                ". Accuracy " + accuracy + " percent. Current streak " + p.streak + "."
        }
    }

    fun levelSummary(language: TriviaGameEngine.Language): String {
        val p = profile()
        return if (language == TriviaGameEngine.Language.CS) {
            "Guess Who level " + p.level + ", " + p.xp + " XP."
        } else {
            "Guess Who level " + p.level + ", with " + p.xp + " XP."
        }
    }

    fun roundSummary(language: TriviaGameEngine.Language): String {
        val accuracy = if (roundAnswered == 0) 0 else roundCorrect * 100 / roundAnswered
        return if (language == TriviaGameEngine.Language.CS) {
            "Kolo Guess Who dokončeno. " + roundCorrect + " z " + roundAnswered +
                " osobností správně. Úspěšnost " + accuracy + " procent."
        } else {
            "Guess Who round complete. " + roundCorrect + " out of " + roundAnswered +
                " people correct. Accuracy " + accuracy + " percent."
        }
    }

    fun resetRound() {
        roundAnswered = 0
        roundCorrect = 0
        currentPersonId = null
        hintIndex = 0
        prefs.edit()
            .putInt(KEY_ROUND_ANSWERED, 0)
            .putInt(KEY_ROUND_CORRECT, 0)
            .remove(KEY_CURRENT_PERSON)
            .putInt(KEY_HINT_INDEX, 0)
            .apply()
    }

    private fun registerMiss(): Boolean {
        val person = startOrResume()
        val p = profile()

        sessionStreak = 0
        roundAnswered += 1

        prefs.edit()
            .putInt(KEY_STREAK, 0)
            .putInt(KEY_TOTAL_ANSWERED, p.totalAnswered + 1)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_ROUND_CORRECT, roundCorrect)
            .putInt(seenKey(person.id), seenCount(person.id) + 1)
            .remove(KEY_CURRENT_PERSON)
            .putInt(KEY_HINT_INDEX, 0)
            .apply()

        currentPersonId = null
        hintIndex = 0
        return roundAnswered >= ROUND_SIZE
    }

    private fun normalize(
        value: String,
        language: TriviaGameEngine.Language
    ): String {
        return Normalizer.normalize(
            value.lowercase(language.locale),
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun nameMatches(actual: String, expected: String): Boolean {
        if (actual == expected) return true
        if (expected.length >= 5 && actual.contains(expected)) return true
        if (actual.length >= 5 && expected.contains(actual)) return true

        val expectedTokens = expected.split(" ")
        val actualTokens = actual.split(" ")
        if (expectedTokens.isNotEmpty() && actualTokens.isNotEmpty()) {
            val expectedLast = expectedTokens.last()
            val actualLast = actualTokens.last()
            if (expectedLast.length >= 4 && actualLast == expectedLast) return true
        }

        val tolerance = when {
            expected.length <= 6 -> 0
            expected.length <= 12 -> 1
            else -> 2
        }
        return levenshtein(actual, expected) <= tolerance
    }

    private fun levelForXp(xp: Int): Int = when {
        xp >= 1800 -> 5
        xp >= 900 -> 4
        xp >= 400 -> 3
        xp >= 150 -> 2
        else -> 1
    }

    private fun seenCount(id: String) = prefs.getInt(seenKey(id), 0)
    private fun correctCount(id: String) = prefs.getInt(correctKey(id), 0)
    private fun seenKey(id: String) = "seen_" + id
    private fun correctKey(id: String) = "correct_" + id

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(
                    min(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    companion object {
        private const val PREFS = "daw_guess_who"
        private const val KEY_XP = "xp"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_TOTAL_ANSWERED = "total_answered"
        private const val KEY_TOTAL_CORRECT = "total_correct"
        private const val KEY_ROUND_ANSWERED = "round_answered"
        private const val KEY_ROUND_CORRECT = "round_correct"
        private const val KEY_CURRENT_PERSON = "current_person"
        private const val KEY_HINT_INDEX = "hint_index"
        private const val KEY_RECENT = "recent"
        private const val ROUND_SIZE = 10
        private const val MAX_HINT_INDEX = 2

        private val bundledPeople = listOf(
            Person("cz001","Karel Gott",listOf("Gott","Kája Gott"),Region.CZECH,1,
                listOf("I was one of the best-known Czech singers.","I repeatedly won the Golden Nightingale music poll.","I was nicknamed the Sinatra of the East."),
                listOf("Patřím mezi nejznámější české zpěváky.","Mnohokrát jsem vyhrál anketu Zlatý slavík.","Přezdívalo se mi Sinatra východu.")),
            Person("cz002","Jaromír Jágr",listOf("Jagr","Jaromir Jagr"),Region.CZECH,1,
                listOf("I am a famous Czech ice hockey player.","I wore number 68 for most of my career.","I played many seasons in the NHL and for Kladno."),
                listOf("Jsem slavný český hokejista.","Většinu kariéry jsem nosil číslo 68.","Hrál jsem dlouhé roky v NHL a také za Kladno.")),
            Person("cz003","Emil Zátopek",listOf("Zatopek","Emil Zatopek"),Region.CZECH,2,
                listOf("I was a Czech long-distance runner.","I won three gold medals at the 1952 Helsinki Olympics.","I was known as the Czech Locomotive."),
                listOf("Byl jsem český vytrvalostní běžec.","Na olympiádě v Helsinkách 1952 jsem získal tři zlaté medaile.","Přezdívalo se mi Česká lokomotiva.")),
            Person("cz004","Věra Čáslavská",listOf("Caslavska","Vera Caslavska"),Region.CZECH,2,
                listOf("I was a Czech gymnast.","I won seven Olympic gold medals.","I became one of the most successful Czech Olympians."),
                listOf("Byla jsem česká gymnastka.","Získala jsem sedm olympijských zlatých medailí.","Patřím mezi nejúspěšnější české olympioniky.")),
            Person("cz005","Martina Navrátilová",listOf("Navratilova","Martina Navratilova"),Region.CZECH,2,
                listOf("I was born in Czechoslovakia and became a tennis legend.","I won Wimbledon singles nine times.","I was famous for an attacking serve-and-volley style."),
                listOf("Narodila jsem se v Československu a stala se tenisovou legendou.","Wimbledonskou dvouhru jsem vyhrála devětkrát.","Byla jsem známá útočným stylem servis-volej.")),
            Person("cz006","Petr Čech",listOf("Petr Cech","Cech"),Region.CZECH,1,
                listOf("I am a Czech former football goalkeeper.","I played for Chelsea and Arsenal.","I often played wearing protective headgear."),
                listOf("Jsem bývalý český fotbalový brankář.","Hrál jsem za Chelsea a Arsenal.","Často jsem chytal v ochranné helmě.")),
            Person("cz007","Ivan Lendl",listOf("Lendl","Ivan Lendl"),Region.CZECH,2,
                listOf("I am a Czech-born tennis champion.","I won eight Grand Slam singles titles.","I later coached Andy Murray."),
                listOf("Jsem tenisový šampion narozený v Československu.","Vyhrál jsem osm grandslamových titulů ve dvouhře.","Později jsem trénoval Andyho Murrayho.")),
            Person("cz008","Jiří Procházka",listOf("Jiri Prochazka","Prochazka"),Region.CZECH,2,
                listOf("I am a Czech mixed martial artist.","I became a UFC light heavyweight champion.","My nickname is BJP."),
                listOf("Jsem český zápasník MMA.","Stal jsem se šampionem UFC v polotěžké váze.","Moje přezdívka je BJP.")),
            Person("cz009","Ester Ledecká",listOf("Ester Ledecka","Ledecka"),Region.CZECH,2,
                listOf("I am a Czech winter sports athlete.","I compete in both alpine skiing and snowboarding.","At the 2018 Olympics I won gold in two different sports."),
                listOf("Jsem česká závodnice v zimních sportech.","Závodím v alpském lyžování i snowboardingu.","Na olympiádě 2018 jsem vyhrála zlato ve dvou různých sportech.")),
            Person("cz010","Jan Železný",listOf("Jan Zelezny","Zelezny"),Region.CZECH,2,
                listOf("I am a Czech track and field legend.","My discipline was the javelin throw.","I won three Olympic gold medals."),
                listOf("Jsem česká atletická legenda.","Mou disciplínou byl hod oštěpem.","Vyhrál jsem tři olympijské zlaté medaile.")),
            Person("cz011","Tomáš Baťa",listOf("Tomas Bata","Bata"),Region.CZECH,3,
                listOf("I was a Czech entrepreneur.","My company became famous for making shoes.","My name is strongly connected with the city of Zlín."),
                listOf("Byl jsem český podnikatel.","Moje firma se proslavila výrobou obuvi.","Moje jméno je silně spojené se Zlínem.")),
            Person("cz012","Antonín Dvořák",listOf("Antonin Dvorak","Dvorak"),Region.CZECH,2,
                listOf("I was a Czech classical composer.","I composed the New World Symphony.","I spent part of my career in the United States."),
                listOf("Byl jsem český skladatel klasické hudby.","Složil jsem Novosvětskou symfonii.","Část kariéry jsem strávil ve Spojených státech.")),
            Person("cz013","Bedřich Smetana",listOf("Bedrich Smetana","Smetana"),Region.CZECH,2,
                listOf("I was a Czech composer.","I composed the cycle Má vlast.","One of my best-known works depicts the Vltava river."),
                listOf("Byl jsem český skladatel.","Složil jsem cyklus Má vlast.","Jedna z mých nejslavnějších skladeb znázorňuje řeku Vltavu.")),
            Person("cz014","Alfons Mucha",listOf("Mucha","Alphonse Mucha"),Region.CZECH,2,
                listOf("I was a Czech artist associated with Art Nouveau.","I became famous for decorative posters in Paris.","I created the Slav Epic."),
                listOf("Byl jsem český umělec spojený se secesí.","Proslavily mě dekorativní plakáty v Paříži.","Vytvořil jsem Slovanskou epopej.")),
            Person("cz015","Miloš Forman",listOf("Milos Forman","Forman"),Region.CZECH,2,
                listOf("I was a Czech-born film director.","I directed One Flew Over the Cuckoo's Nest.","I also directed Amadeus."),
                listOf("Byl jsem filmový režisér narozený v Československu.","Režíroval jsem Přelet nad kukaččím hnízdem.","Režíroval jsem také film Amadeus.")),
            Person("cz016","Karel Čapek",listOf("Karel Capek","Capek"),Region.CZECH,3,
                listOf("I was a Czech writer and playwright.","My play R.U.R. helped popularize a famous word.","That word was robot."),
                listOf("Byl jsem český spisovatel a dramatik.","Moje hra R.U.R. proslavila jedno známé slovo.","Tím slovem je robot.")),
            Person("cz017","Jaroslav Hašek",listOf("Jaroslav Hasek","Hasek"),Region.CZECH,3,
                listOf("I was a Czech writer.","My most famous character is a soldier during World War One.","I wrote The Good Soldier Švejk."),
                listOf("Byl jsem český spisovatel.","Mou nejslavnější postavou je voják z první světové války.","Napsal jsem Osudy dobrého vojáka Švejka.")),
            Person("cz018","Ewa Farna",listOf("Farna","Ewa Farna"),Region.CZECH,2,
                listOf("I am a singer active in the Czech and Polish music scenes.","I began my career as a teenager.","I perform in both Czech and Polish."),
                listOf("Jsem zpěvačka působící na české i polské scéně.","Kariéru jsem začala už jako teenager.","Zpívám česky i polsky.")),
            Person("cz019","Lucie Bílá",listOf("Lucie Bila","Bila"),Region.CZECH,2,
                listOf("I am a well-known Czech singer.","I have repeatedly won the Czech Nightingale poll.","My stage surname means white in Czech."),
                listOf("Jsem známá česká zpěvačka.","Mnohokrát jsem vyhrála Českého slavíka.","Moje umělecké příjmení znamená bílou barvu.")),
            Person("cz020","Jiřina Bohdalová",listOf("Jirina Bohdalova","Bohdalova"),Region.CZECH,2,
                listOf("I am a Czech actress with a very long career.","I have worked extensively in film, television and fairy tales.","My first name is Jiřina."),
                listOf("Jsem česká herečka s velmi dlouhou kariérou.","Hrála jsem ve filmu, televizi i mnoha pohádkách.","Jmenuji se Jiřina.")),

            Person("w001","Albert Einstein",listOf("Einstein"),Region.WORLD,1,
                listOf("I was a theoretical physicist.","I developed the theory of relativity.","My name is strongly associated with E equals m c squared."),
                listOf("Byl jsem teoretický fyzik.","Vytvořil jsem teorii relativity.","Moje jméno je spojené s rovnicí E rovná se m c na druhou.")),
            Person("w002","Marie Curie",listOf("Curie","Maria Sklodowska Curie"),Region.WORLD,1,
                listOf("I was a pioneering scientist in radioactivity.","I won Nobel Prizes in two different sciences.","I discovered polonium and radium with my husband."),
                listOf("Byla jsem průkopnicí výzkumu radioaktivity.","Získala jsem Nobelovy ceny ve dvou různých vědních oborech.","S manželem jsem objevila polonium a radium.")),
            Person("w003","Isaac Newton",listOf("Newton"),Region.WORLD,2,
                listOf("I was an English scientist and mathematician.","I formulated laws of motion and universal gravitation.","A famous story links me with a falling apple."),
                listOf("Byl jsem anglický vědec a matematik.","Formuloval jsem zákony pohybu a gravitace.","Známý příběh mě spojuje s padajícím jablkem.")),
            Person("w004","Nikola Tesla",listOf("Tesla"),Region.WORLD,2,
                listOf("I was an inventor and electrical engineer.","My work is closely associated with alternating current systems.","A modern electric car company uses my surname."),
                listOf("Byl jsem vynálezce a elektrotechnický inženýr.","Moje práce je úzce spojená se střídavým proudem.","Moderní automobilka s elektromobily používá moje příjmení.")),
            Person("w005","Leonardo da Vinci",listOf("Leonardo","Da Vinci"),Region.WORLD,1,
                listOf("I was an Italian Renaissance polymath.","I painted The Last Supper.","I painted the Mona Lisa."),
                listOf("Byl jsem italský renesanční všeuměl.","Namaloval jsem Poslední večeři.","Namaloval jsem Monu Lisu.")),
            Person("w006","Vincent van Gogh",listOf("Van Gogh","Vincent"),Region.WORLD,2,
                listOf("I was a Dutch painter.","I painted The Starry Night.","A famous story about me involves my ear."),
                listOf("Byl jsem nizozemský malíř.","Namaloval jsem Hvězdnou noc.","Známý příběh o mně se týká mého ucha.")),
            Person("w007","Pablo Picasso",listOf("Picasso"),Region.WORLD,2,
                listOf("I was a Spanish artist.","I co-founded the Cubist movement.","I painted Guernica."),
                listOf("Byl jsem španělský umělec.","Patřil jsem k zakladatelům kubismu.","Namaloval jsem Guernicu.")),
            Person("w008","William Shakespeare",listOf("Shakespeare"),Region.WORLD,1,
                listOf("I was an English playwright and poet.","I wrote Macbeth.","I also wrote Romeo and Juliet."),
                listOf("Byl jsem anglický dramatik a básník.","Napsal jsem Macbetha.","Napsal jsem také Romea a Julii.")),
            Person("w009","Wolfgang Amadeus Mozart",listOf("Mozart","Wolfgang Mozart"),Region.WORLD,1,
                listOf("I was an Austrian classical composer.","I was a famous child prodigy.","I composed The Magic Flute."),
                listOf("Byl jsem rakouský skladatel klasické hudby.","Byl jsem známé zázračné dítě.","Složil jsem Kouzelnou flétnu.")),
            Person("w010","Ludwig van Beethoven",listOf("Beethoven"),Region.WORLD,1,
                listOf("I was a German composer.","I continued composing despite severe hearing loss.","My Ninth Symphony includes Ode to Joy."),
                listOf("Byl jsem německý skladatel.","Skládal jsem i přes vážnou ztrátu sluchu.","Moje Devátá symfonie obsahuje Ódu na radost.")),
            Person("w011","Michael Jackson",listOf("Jackson","Michael Jackson"),Region.WORLD,1,
                listOf("I was an American pop singer.","I was known for the moonwalk.","My album Thriller became one of the best-selling albums ever."),
                listOf("Byl jsem americký popový zpěvák.","Proslavil mě moonwalk.","Moje album Thriller patří k nejprodávanějším albům historie.")),
            Person("w012","Freddie Mercury",listOf("Mercury","Freddie Mercury"),Region.WORLD,1,
                listOf("I was the lead singer of a British rock band.","I performed at Live Aid in 1985.","My band was Queen."),
                listOf("Byl jsem zpěvák britské rockové kapely.","Vystupoval jsem na Live Aid v roce 1985.","Mou kapelou byli Queen.")),
            Person("w013","Elvis Presley",listOf("Elvis","Presley"),Region.WORLD,1,
                listOf("I was an American singer and actor.","I became a symbol of early rock and roll.","I was called the King of Rock and Roll."),
                listOf("Byl jsem americký zpěvák a herec.","Stal jsem se symbolem raného rock and rollu.","Říkalo se mi Král rock and rollu.")),
            Person("w014","Taylor Swift",listOf("Taylor","Swift"),Region.WORLD,1,
                listOf("I am an American singer-songwriter.","My career began strongly in country music before expanding into pop.","My fans are often called Swifties."),
                listOf("Jsem americká zpěvačka a autorka písní.","Kariéru jsem začínala hlavně v country a později přešla i k popu.","Mým fanouškům se často říká Swifties.")),
            Person("w015","Adele",listOf("Adele Adkins"),Region.WORLD,1,
                listOf("I am a British singer.","My albums include 19, 21, 25 and 30.","One of my biggest songs is Someone Like You."),
                listOf("Jsem britská zpěvačka.","Moje alba mají názvy 19, 21, 25 a 30.","Jedním z mých největších hitů je Someone Like You.")),
            Person("w016","Tom Hanks",listOf("Hanks","Tom Hanks"),Region.WORLD,1,
                listOf("I am an American actor.","I played the title character in Forrest Gump.","I also starred in Cast Away."),
                listOf("Jsem americký herec.","Hrál jsem hlavní roli ve Forrestu Gumpovi.","Hrál jsem také ve filmu Trosečník.")),
            Person("w017","Leonardo DiCaprio",listOf("DiCaprio","Leonardo Dicaprio"),Region.WORLD,1,
                listOf("I am an American actor.","I starred in Titanic.","I won an acting Oscar for The Revenant."),
                listOf("Jsem americký herec.","Hrál jsem ve filmu Titanic.","Oscara za herectví jsem získal za Revenant.")),
            Person("w018","Arnold Schwarzenegger",listOf("Schwarzenegger","Arnold"),Region.WORLD,1,
                listOf("I became famous as a bodybuilder and actor.","I starred in The Terminator.","I was born in Austria."),
                listOf("Proslavil jsem se jako kulturista a herec.","Hrál jsem v Terminátorovi.","Narodil jsem se v Rakousku.")),
            Person("w019","Keanu Reeves",listOf("Reeves","Keanu"),Region.WORLD,2,
                listOf("I am a Canadian actor.","I played Neo in a science-fiction film series.","That series is The Matrix."),
                listOf("Jsem kanadský herec.","Hrál jsem Nea ve slavné sci-fi filmové sérii.","Tou sérií je Matrix.")),
            Person("w020","Morgan Freeman",listOf("Freeman","Morgan Freeman"),Region.WORLD,2,
                listOf("I am an American actor known for a distinctive voice.","I starred in The Shawshank Redemption.","I played God in Bruce Almighty."),
                listOf("Jsem americký herec známý výrazným hlasem.","Hrál jsem ve Vykoupení z věznice Shawshank.","Hrál jsem Boha ve filmu Božský Bruce.")),
            Person("w021","Cristiano Ronaldo",listOf("Ronaldo","Cristiano"),Region.WORLD,1,
                listOf("I am a Portuguese footballer.","I wore number 7 at clubs including Manchester United and Real Madrid.","My first name is Cristiano."),
                listOf("Jsem portugalský fotbalista.","Nosil jsem číslo 7 například v Manchesteru United a Realu Madrid.","Jmenuji se Cristiano.")),
            Person("w022","Lionel Messi",listOf("Messi","Leo Messi"),Region.WORLD,1,
                listOf("I am an Argentine footballer.","I spent most of my club career at Barcelona.","I captained Argentina to the 2022 World Cup title."),
                listOf("Jsem argentinský fotbalista.","Většinu klubové kariéry jsem strávil v Barceloně.","Jako kapitán jsem dovedl Argentinu k titulu mistra světa 2022.")),
            Person("w023","Michael Jordan",listOf("Jordan","Michael Jordan"),Region.WORLD,1,
                listOf("I am an American basketball legend.","I wore number 23 for the Chicago Bulls.","My surname became a famous Nike shoe brand."),
                listOf("Jsem americká basketbalová legenda.","Za Chicago Bulls jsem nosil číslo 23.","Moje příjmení se stalo známou značkou bot Nike.")),
            Person("w024","LeBron James",listOf("LeBron","Lebron James"),Region.WORLD,1,
                listOf("I am an American basketball player.","I have played for Cleveland, Miami and Los Angeles.","My nickname is King James."),
                listOf("Jsem americký basketbalista.","Hrál jsem za Cleveland, Miami a Los Angeles.","Přezdívá se mi King James.")),
            Person("w025","Roger Federer",listOf("Federer","Roger Federer"),Region.WORLD,1,
                listOf("I am a Swiss tennis legend.","I won Wimbledon men's singles eight times.","I was known for an elegant one-handed backhand."),
                listOf("Jsem švýcarská tenisová legenda.","Wimbledonskou dvouhru jsem vyhrál osmkrát.","Byl jsem známý elegantním jednoručným bekhendem.")),
            Person("w026","Rafael Nadal",listOf("Nadal","Rafa Nadal"),Region.WORLD,1,
                listOf("I am a Spanish tennis legend.","I am especially associated with clay courts.","I won the French Open a record number of times."),
                listOf("Jsem španělská tenisová legenda.","Jsem spojený hlavně s antukou.","French Open jsem vyhrál rekordně mnohokrát.")),
            Person("w027","Usain Bolt",listOf("Bolt","Usain"),Region.WORLD,1,
                listOf("I am a Jamaican sprinter.","I won Olympic gold medals in the 100 and 200 meters.","I became known as the fastest man in the world."),
                listOf("Jsem jamajský sprinter.","Vyhrál jsem olympijské zlato na 100 i 200 metrů.","Proslavil jsem se jako nejrychlejší muž světa.")),
            Person("w028","Serena Williams",listOf("Serena","Williams"),Region.WORLD,1,
                listOf("I am an American tennis champion.","I won 23 Grand Slam singles titles.","My sister Venus is also a tennis champion."),
                listOf("Jsem americká tenisová šampionka.","Vyhrála jsem 23 grandslamových titulů ve dvouhře.","Moje sestra Venus je také tenisová šampionka.")),
            Person("w029","Tiger Woods",listOf("Tiger","Woods","Tiger Woods"),Region.WORLD,1,
                listOf("I am an American golfer.","I became one of the most successful golfers in history.","My first name is actually Eldrick, but I am known by a feline nickname."),
                listOf("Jsem americký golfista.","Patřím mezi nejúspěšnější golfisty historie.","Jmenuji se Eldrick, ale známý jsem pod kočičí přezdívkou.")),
            Person("w030","Michael Schumacher",listOf("Schumacher","Michael Schumacher"),Region.WORLD,1,
                listOf("I am a German racing driver.","I won seven Formula One world championships.","I became strongly associated with Ferrari."),
                listOf("Jsem německý automobilový závodník.","Vyhrál jsem sedm titulů mistra světa Formule 1.","Silně jsem spojený s Ferrari.")),
            Person("w031","Ayrton Senna",listOf("Senna","Ayrton Senna"),Region.WORLD,2,
                listOf("I was a Brazilian racing driver.","I won three Formula One world championships.","I died after a crash at Imola in 1994."),
                listOf("Byl jsem brazilský automobilový závodník.","Vyhrál jsem tři tituly mistra světa Formule 1.","Zemřel jsem po nehodě v Imole v roce 1994.")),
            Person("w032","Muhammad Ali",listOf("Ali","Cassius Clay"),Region.WORLD,1,
                listOf("I was an American heavyweight boxer.","My original name was Cassius Clay.","I said I float like a butterfly and sting like a bee."),
                listOf("Byl jsem americký boxer těžké váhy.","Původně jsem se jmenoval Cassius Clay.","Říkal jsem, že plavu jako motýl a bodám jako včela.")),
            Person("w033","Bruce Lee",listOf("Lee","Bruce Lee"),Region.WORLD,1,
                listOf("I was a martial artist and actor.","I helped popularize martial arts cinema worldwide.","I developed Jeet Kune Do."),
                listOf("Byl jsem mistr bojových umění a herec.","Pomohl jsem celosvětově proslavit filmy s bojovými uměními.","Vytvořil jsem Jeet Kune Do.")),
            Person("w034","Steve Jobs",listOf("Jobs","Steve Jobs"),Region.WORLD,1,
                listOf("I was an American technology entrepreneur.","I co-founded Apple.","I introduced products including the iPhone on stage."),
                listOf("Byl jsem americký technologický podnikatel.","Spoluzaložil jsem Apple.","Na pódiu jsem představoval produkty včetně iPhonu.")),
            Person("w035","Bill Gates",listOf("Gates","Bill Gates"),Region.WORLD,1,
                listOf("I am an American technology entrepreneur.","I co-founded Microsoft.","I later became known for large-scale philanthropy."),
                listOf("Jsem americký technologický podnikatel.","Spoluzaložil jsem Microsoft.","Později jsem se proslavil také rozsáhlou filantropií.")),
            Person("w036","Mark Zuckerberg",listOf("Zuckerberg","Mark Zuckerberg"),Region.WORLD,2,
                listOf("I am an American technology entrepreneur.","I launched a social network while at Harvard.","I co-founded Facebook."),
                listOf("Jsem americký technologický podnikatel.","Během studia na Harvardu jsem spustil sociální síť.","Spoluzaložil jsem Facebook.")),
            Person("w037","Stephen Hawking",listOf("Hawking","Stephen Hawking"),Region.WORLD,2,
                listOf("I was a British theoretical physicist.","I wrote A Brief History of Time.","I became famous for work on black holes."),
                listOf("Byl jsem britský teoretický fyzik.","Napsal jsem Stručnou historii času.","Proslavil jsem se výzkumem černých děr.")),
            Person("w038","Neil Armstrong",listOf("Armstrong","Neil Armstrong"),Region.WORLD,1,
                listOf("I was an American astronaut.","I flew on Apollo 11.","I became the first person to walk on the Moon."),
                listOf("Byl jsem americký astronaut.","Letěl jsem v misi Apollo 11.","Stal jsem se prvním člověkem, který vstoupil na Měsíc.")),
            Person("w039","Yuri Gagarin",listOf("Gagarin","Jurij Gagarin"),Region.WORLD,2,
                listOf("I was a Soviet cosmonaut.","I flew aboard Vostok 1 in 1961.","I became the first human in outer space."),
                listOf("Byl jsem sovětský kosmonaut.","V roce 1961 jsem letěl lodí Vostok 1.","Stal jsem se prvním člověkem ve vesmíru.")),
            Person("w040","Amelia Earhart",listOf("Earhart","Amelia Earhart"),Region.WORLD,2,
                listOf("I was an American aviation pioneer.","I was the first woman to fly solo across the Atlantic.","I disappeared during an attempt to fly around the world."),
                listOf("Byla jsem americká průkopnice letectví.","Jako první žena jsem sama přeletěla Atlantik.","Zmizela jsem při pokusu obletět svět.")),
            Person("w041","Charlie Chaplin",listOf("Chaplin","Charlie Chaplin"),Region.WORLD,2,
                listOf("I was a British actor and filmmaker.","I became an icon of silent cinema.","My famous character wore a bowler hat and carried a cane."),
                listOf("Byl jsem britský herec a filmař.","Stal jsem se ikonou němého filmu.","Moje slavná postava nosila buřinku a hůlku.")),
            Person("w042","Audrey Hepburn",listOf("Hepburn","Audrey Hepburn"),Region.WORLD,2,
                listOf("I was a British actress and humanitarian.","I starred in Roman Holiday.","I am closely associated with Breakfast at Tiffany's."),
                listOf("Byla jsem britská herečka a humanitární pracovnice.","Hrála jsem v Prázdninách v Římě.","Silně jsem spojená s filmem Snídaně u Tiffanyho.")),
            Person("w043","Marilyn Monroe",listOf("Monroe","Marilyn Monroe"),Region.WORLD,1,
                listOf("I was an American actress and model.","I became a major Hollywood icon of the 1950s.","I starred in Some Like It Hot."),
                listOf("Byla jsem americká herečka a modelka.","Stala jsem se hollywoodskou ikonou padesátých let.","Hrála jsem ve filmu Někdo to rád horké.")),
            Person("w044","Walt Disney",listOf("Disney","Walt Disney"),Region.WORLD,1,
                listOf("I was an American animator and entrepreneur.","My company created Mickey Mouse.","My surname became the name of a global entertainment company and theme parks."),
                listOf("Byl jsem americký animátor a podnikatel.","Moje společnost vytvořila Mickey Mouse.","Moje příjmení nese globální zábavní společnost i zábavní parky.")),
            Person("w045","J. K. Rowling",listOf("Rowling","JK Rowling","Joanne Rowling"),Region.WORLD,1,
                listOf("I am a British author.","I wrote a bestselling fantasy series about a young wizard.","That wizard is Harry Potter."),
                listOf("Jsem britská spisovatelka.","Napsala jsem slavnou fantasy sérii o mladém kouzelníkovi.","Tím kouzelníkem je Harry Potter.")),
            Person("w046","Agatha Christie",listOf("Christie","Agatha Christie"),Region.WORLD,2,
                listOf("I was a British mystery writer.","I created Hercule Poirot.","I also created Miss Marple."),
                listOf("Byla jsem britská autorka detektivek.","Vytvořila jsem Hercula Poirota.","Vytvořila jsem také slečnu Marplovou.")),
            Person("w047","Ernest Hemingway",listOf("Hemingway","Ernest Hemingway"),Region.WORLD,3,
                listOf("I was an American writer.","I wrote The Old Man and the Sea.","I won the Nobel Prize in Literature in 1954."),
                listOf("Byl jsem americký spisovatel.","Napsal jsem Stařec a moře.","V roce 1954 jsem získal Nobelovu cenu za literaturu.")),
            Person("w048","Frida Kahlo",listOf("Kahlo","Frida Kahlo"),Region.WORLD,2,
                listOf("I was a Mexican painter.","I became famous for many self-portraits.","My work often explored identity, pain and Mexican culture."),
                listOf("Byla jsem mexická malířka.","Proslavila jsem se mnoha autoportréty.","Má tvorba často zkoumala identitu, bolest a mexickou kulturu.")),
            Person("w049","Coco Chanel",listOf("Chanel","Coco Chanel"),Region.WORLD,2,
                listOf("I was a French fashion designer.","My brand became famous for a fragrance called Number Five.","My surname is one of the world's best-known fashion labels."),
                listOf("Byla jsem francouzská módní návrhářka.","Má značka se proslavila parfémem číslo pět.","Moje příjmení patří k nejslavnějším módním značkám světa.")),
            Person("w050","Gordon Ramsay",listOf("Ramsay","Gordon Ramsay"),Region.WORLD,1,
                listOf("I am a British chef and television personality.","I am known for a fiery style on cooking shows.","I host shows including Hell's Kitchen."),
                listOf("Jsem britský šéfkuchař a televizní osobnost.","Jsem známý výbušným stylem v kuchařských pořadech.","Moderuji pořady včetně Hell's Kitchen."))
        )


        private val people: List<Person>
            get() = LiveContentRepository.guessWhoPeople().ifEmpty { bundledPeople }
    }
}
