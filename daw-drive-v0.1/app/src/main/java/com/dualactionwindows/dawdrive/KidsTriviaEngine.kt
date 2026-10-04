package com.dualactionwindows.dawdrive

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

class KidsTriviaEngine(context: Context) {

    init {
        LiveContentRepository.initialize(context)
    }

    enum class Category(val key: String) {
        ANIMALS("animals"),
        MOVIES("movies"),
        CARS("cars"),
        SPACE("space"),
        NATURE("nature"),
        BODY("body"),
        SPORTS("sports"),
        FOOTBALL("football"),
        FOOD_WORLD("food_world"),
        LOGIC("logic"),
        FAIRY_TALES("fairy_tales"),
        SCHOOL("school"),
        CZECHIA("czechia"),
        SONGS("songs")
    }

    data class Question(
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

    data class LocalizedQuestion(
        val id: String,
        val difficulty: Int,
        val category: Category,
        val categoryName: String,
        val prompt: String,
        val answers: List<String>,
        val explanation: String
    )

    data class Profile(
        val level: Int,
        val xp: Int,
        val streak: Int,
        val bestStreak: Int,
        val totalAnswered: Int,
        val totalCorrect: Int,
        val roundAnswered: Int,
        val roundCorrect: Int,
        val accuracy: Int
    )

    data class AnswerResult(
        val correct: Boolean,
        val expected: String,
        val explanation: String,
        val xpEarned: Int,
        val level: Int,
        val promoted: Boolean,
        val streak: Int,
        val roundFinished: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var currentQuestionId: String? = prefs.getString(KEY_CURRENT, null)
    private var sessionStreak = prefs.getInt(KEY_STREAK, 0)
    private var roundAnswered = prefs.getInt(KEY_ROUND_ANSWERED, 0)
    private var roundCorrect = prefs.getInt(KEY_ROUND_CORRECT, 0)

    fun profile(): Profile {
        val xp = prefs.getInt(KEY_XP, 0)
        val totalAnswered = prefs.getInt(KEY_TOTAL_ANSWERED, 0)
        val totalCorrect = prefs.getInt(KEY_TOTAL_CORRECT, 0)
        return Profile(
            level = levelForXp(xp),
            xp = xp,
            streak = sessionStreak,
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            totalAnswered = totalAnswered,
            totalCorrect = totalCorrect,
            roundAnswered = roundAnswered,
            roundCorrect = roundCorrect,
            accuracy = if (totalAnswered == 0) 0 else totalCorrect * 100 / totalAnswered
        )
    }

    fun currentQuestion(language: TriviaGameEngine.Language): LocalizedQuestion? =
        currentQuestionId?.let { id ->
            questionsFor(language).firstOrNull { it.id == id }
        }?.let {
            localize(it, language)
        }

    fun startOrResume(language: TriviaGameEngine.Language): LocalizedQuestion =
        currentQuestion(language) ?: nextQuestion(language)

    fun nextQuestion(language: TriviaGameEngine.Language): LocalizedQuestion {
        if (roundAnswered >= ROUND_SIZE) resetRound()

        val p = profile()
        val maxDifficulty = when {
            p.level <= 1 -> 1
            p.level <= 3 -> 2
            else -> 3
        }

        val recent = prefs.getString(KEY_RECENT, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val pool = questionsFor(language)
        val eligible = pool.filter { it.difficulty <= maxDifficulty }
        val lastCategory = prefs.getString(KEY_LAST_CATEGORY, null)

        val availableCategories = eligible
            .map { it.category }
            .distinct()

        val categoryPool = availableCategories
            .filterNot { it.key == lastCategory }
            .ifEmpty { availableCategories }

        val selectedCategory = categoryPool.random()
        val categoryQuestions = eligible.filter { it.category == selectedCategory }

        val ranked = categoryQuestions
            .asSequence()
            .filterNot { recent.contains(it.id) }
            .map { q ->
                val seen = seenCount(q.id)
                val wrong = wrongCount(q.id)
                var score = 100.0
                if (seen == 0) score += 35.0
                score -= seen * 7.0
                score += wrong * 6.0
                q to score
            }
            .sortedByDescending { it.second }
            .take(RANDOM_TOP_POOL)
            .toList()
            .ifEmpty {
                categoryQuestions.map { q ->
                    q to (100.0 - seenCount(q.id) * 7.0 + wrongCount(q.id) * 6.0)
                }.sortedByDescending { it.second }.take(RANDOM_TOP_POOL)
            }

        val selected = if (ranked.isEmpty()) {
            categoryQuestions.random()
        } else {
            val floor = ranked.minOf { it.second }
            val weighted = ranked.map { (q, score) ->
                q to maxOf(1.0, score - floor + 8.0)
            }
            val totalWeight = weighted.sumOf { it.second }
            var pick = Random.nextDouble(totalWeight)
            weighted.firstOrNull { (_, weight) ->
                pick -= weight
                pick <= 0.0
            }?.first ?: weighted.last().first
        }

        currentQuestionId = selected.id
        prefs.edit()
            .putString(KEY_CURRENT, selected.id)
            .putString(KEY_RECENT, (recent + selected.id).takeLast(14).joinToString(","))
            .putString(KEY_LAST_CATEGORY, selected.category.key)
            .apply()

        return localize(selected, language)
    }

    fun answerCandidates(
        candidates: List<String>,
        language: TriviaGameEngine.Language
    ): AnswerResult {
        val q = currentQuestion(language) ?: startOrResume(language)
        val before = profile()

        val normalizedExpected = q.answers.map { normalize(it, language) }
        val correct = candidates.any { raw ->
            val actual = normalize(raw, language)
            normalizedExpected.any { expected ->
                if (actual == expected) {
                    true
                } else if (expected.length >= 4 && actual.contains(expected)) {
                    true
                } else if (actual.length >= 4 && expected.contains(actual)) {
                    true
                } else {
                    val distance = levenshtein(actual, expected)
                    val maxLen = maxOf(actual.length, expected.length)
                    val similarity = if (maxLen == 0) 0.0
                    else 1.0 - distance.toDouble() / maxLen.toDouble()

                    distance <= when {
                        expected.length <= 4 -> 0
                        expected.length <= 7 -> 1
                        expected.length <= 12 -> 2
                        else -> 3
                    } || similarity >= 0.82
                }
            }
        }

        sessionStreak = if (correct) sessionStreak + 1 else 0
        val xpEarned = if (correct) {
            10 + q.difficulty * 4 +
                when {
                    sessionStreak >= 8 -> 12
                    sessionStreak >= 5 -> 8
                    sessionStreak >= 3 -> 4
                    else -> 0
                }
        } else 1

        val newXp = before.xp + xpEarned
        val newLevel = levelForXp(newXp)

        roundAnswered += 1
        if (correct) roundCorrect += 1

        prefs.edit()
            .putInt(KEY_XP, newXp)
            .putInt(KEY_STREAK, sessionStreak)
            .putInt(KEY_BEST_STREAK, max(before.bestStreak, sessionStreak))
            .putInt(KEY_TOTAL_ANSWERED, before.totalAnswered + 1)
            .putInt(KEY_TOTAL_CORRECT, before.totalCorrect + if (correct) 1 else 0)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .putInt(KEY_ROUND_CORRECT, roundCorrect)
            .putInt(seenKey(q.id), seenCount(q.id) + 1)
            .putInt(correctKey(q.id), correctCount(q.id) + if (correct) 1 else 0)
            .putInt(wrongKey(q.id), wrongCount(q.id) + if (correct) 0 else 1)
            .remove(KEY_CURRENT)
            .apply()

        currentQuestionId = null

        return AnswerResult(
            correct = correct,
            expected = q.answers.first(),
            explanation = q.explanation,
            xpEarned = xpEarned,
            level = newLevel,
            promoted = newLevel > before.level,
            streak = sessionStreak,
            roundFinished = roundAnswered >= ROUND_SIZE
        )
    }

    fun skipCurrent(): Boolean {
        val q = currentQuestion(TriviaGameEngine.Language.EN)
        sessionStreak = 0
        roundAnswered += 1
        q?.let {
            prefs.edit()
                .putInt(seenKey(it.id), seenCount(it.id) + 1)
                .putInt(wrongKey(it.id), wrongCount(it.id) + 1)
                .apply()
        }
        currentQuestionId = null
        prefs.edit()
            .putInt(KEY_STREAK, 0)
            .putInt(KEY_ROUND_ANSWERED, roundAnswered)
            .remove(KEY_CURRENT)
            .apply()
        return roundAnswered >= ROUND_SIZE
    }

    fun scoreSummary(language: TriviaGameEngine.Language): String {
        val p = profile()
        return if (language == TriviaGameEngine.Language.CS) {
            "Trivia Kids. " + p.totalCorrect + " správně z " + p.totalAnswered +
                ". Úspěšnost " + p.accuracy + " procent. Série " + p.streak + "."
        } else {
            "Kids Trivia. " + p.totalCorrect + " correct out of " + p.totalAnswered +
                ". Accuracy " + p.accuracy + " percent. Streak " + p.streak + "."
        }
    }

    fun levelSummary(language: TriviaGameEngine.Language): String {
        val p = profile()
        return if (language == TriviaGameEngine.Language.CS) {
            "Trivia Kids level " + p.level + ". Máš " + p.xp + " XP."
        } else {
            "Kids Trivia level " + p.level + ". You have " + p.xp + " XP."
        }
    }

    fun roundSummary(language: TriviaGameEngine.Language): String {
        val accuracy = if (roundAnswered == 0) 0 else roundCorrect * 100 / roundAnswered
        return if (language == TriviaGameEngine.Language.CS) {
            "Dětské kolo dokončeno. " + roundCorrect + " z " + roundAnswered +
                " správně. Úspěšnost " + accuracy + " procent."
        } else {
            "Kids round complete. " + roundCorrect + " out of " + roundAnswered +
                " correct. Accuracy " + accuracy + " percent."
        }
    }

    fun resetRound() {
        roundAnswered = 0
        roundCorrect = 0
        currentQuestionId = null
        prefs.edit()
            .putInt(KEY_ROUND_ANSWERED, 0)
            .putInt(KEY_ROUND_CORRECT, 0)
            .remove(KEY_CURRENT)
            .apply()
    }

    private fun localize(
        q: Question,
        language: TriviaGameEngine.Language
    ): LocalizedQuestion {
        val cs = language == TriviaGameEngine.Language.CS
        return LocalizedQuestion(
            id = q.id,
            difficulty = q.difficulty,
            category = q.category,
            categoryName = categoryName(q.category, cs),
            prompt = if (cs) q.promptCs else q.promptEn,
            answers = if (cs) q.answersCs else q.answersEn,
            explanation = if (cs) q.explanationCs else q.explanationEn
        )
    }

    private fun categoryName(category: Category, cs: Boolean): String {
        if (!cs) return when (category) {
            Category.ANIMALS -> "Animals"
            Category.MOVIES -> "Movies"
            Category.CARS -> "Cars"
            Category.SPACE -> "Space"
            Category.NATURE -> "Nature"
            Category.BODY -> "Body"
            Category.SPORTS -> "Sports"
            Category.FOOD_WORLD -> "Food and World"
            Category.LOGIC -> "Logic"
            Category.FAIRY_TALES -> "Fairy Tales"
            Category.SCHOOL -> "School"
            Category.CZECHIA -> "Czechia"
            Category.SONGS -> "Songs"
        }

        return when (category) {
            Category.ANIMALS -> "Zvířata"
            Category.MOVIES -> "Filmy"
            Category.CARS -> "Auta"
            Category.SPACE -> "Vesmír"
            Category.NATURE -> "Příroda"
            Category.BODY -> "Lidské tělo"
            Category.SPORTS -> "Sport"
            Category.FOOTBALL -> "Fotbal"
            Category.FOOD_WORLD -> "Jídlo a svět"
            Category.LOGIC -> "Logika"
            Category.FAIRY_TALES -> "Pohádky"
            Category.SCHOOL -> "Škola"
            Category.CZECHIA -> "České reálie"
            Category.SONGS -> "Písničky"
        }
    }

    private fun questionsFor(language: TriviaGameEngine.Language): List<Question> {
        if (language != TriviaGameEngine.Language.CS) return questions

        val czechPack = KidsTriviaQuestionBank.questions.map { q ->
            Question(
                id = "czkid_" + q.id,
                difficulty = when {
                    q.minAge <= 7 -> 1
                    q.minAge <= 9 -> 2
                    else -> 3
                },
                category = when (q.category) {
                    KidsTriviaQuestionBank.Category.ANIMALS -> Category.ANIMALS
                    KidsTriviaQuestionBank.Category.MOVIES -> Category.MOVIES
                    KidsTriviaQuestionBank.Category.CARS -> Category.CARS
                    KidsTriviaQuestionBank.Category.SPACE -> Category.SPACE
                    KidsTriviaQuestionBank.Category.NATURE -> Category.NATURE
                    KidsTriviaQuestionBank.Category.BODY -> Category.BODY
                    KidsTriviaQuestionBank.Category.SPORTS -> Category.SPORTS
                    KidsTriviaQuestionBank.Category.FOOTBALL -> Category.FOOTBALL
                    KidsTriviaQuestionBank.Category.FOOD_WORLD -> Category.FOOD_WORLD
                    KidsTriviaQuestionBank.Category.LOGIC -> Category.LOGIC
                    KidsTriviaQuestionBank.Category.FAIRY_TALES -> Category.FAIRY_TALES
                    KidsTriviaQuestionBank.Category.SCHOOL -> Category.SCHOOL
                    KidsTriviaQuestionBank.Category.CZECHIA -> Category.CZECHIA
                    KidsTriviaQuestionBank.Category.SONGS -> Category.SONGS
                },
                promptEn = q.prompt,
                promptCs = q.prompt,
                answersEn = q.answers,
                answersCs = q.answers,
                explanationEn = q.explanation,
                explanationCs = q.explanation
            )
        }

        return questions + czechPack
    }

    private fun normalize(
        value: String,
        language: TriviaGameEngine.Language
    ): String =
        Normalizer.normalize(
            value.lowercase(language.locale),
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun levelForXp(xp: Int): Int = when {
        xp >= 1300 -> 5
        xp >= 700 -> 4
        xp >= 300 -> 3
        xp >= 100 -> 2
        else -> 1
    }

    private fun seenCount(id: String) = prefs.getInt(seenKey(id), 0)
    private fun correctCount(id: String) = prefs.getInt(correctKey(id), 0)
    private fun wrongCount(id: String) = prefs.getInt(wrongKey(id), 0)
    private fun seenKey(id: String) = "seen_" + id
    private fun correctKey(id: String) = "correct_" + id
    private fun wrongKey(id: String) = "wrong_" + id

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
                current[j + 1] = minOf(
                    minOf(current[j] + 1, previous[j + 1] + 1),
                    previous[j] + cost
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    companion object {
        private const val PREFS = "daw_kids_trivia"
        private const val KEY_XP = "xp"
        private const val KEY_STREAK = "streak"
        private const val KEY_BEST_STREAK = "best_streak"
        private const val KEY_TOTAL_ANSWERED = "total_answered"
        private const val KEY_TOTAL_CORRECT = "total_correct"
        private const val KEY_ROUND_ANSWERED = "round_answered"
        private const val KEY_ROUND_CORRECT = "round_correct"
        private const val KEY_CURRENT = "current"
        private const val KEY_RECENT = "recent"
        private const val KEY_LAST_CATEGORY = "last_category"
        private const val RANDOM_TOP_POOL = 20
        private const val ROUND_SIZE = 10

        private fun q(
            id: String,
            difficulty: Int,
            category: Category,
            en: String,
            cs: String,
            answersEn: List<String>,
            answersCs: List<String>,
            explanationEn: String,
            explanationCs: String
        ) = Question(
            id, difficulty, category, en, cs,
            answersEn, answersCs, explanationEn, explanationCs
        )

        private val bundledQuestions = listOf(
            // Animals
            q("ka001",1,Category.ANIMALS,"Which animal says meow?","Které zvíře dělá mňau?",listOf("cat"),listOf("kocka","kočka"),"A cat meows.","Kočka dělá mňau."),
            q("ka002",1,Category.ANIMALS,"Which animal is known as man's best friend?","Kterému zvířeti se říká nejlepší přítel člověka?",listOf("dog"),listOf("pes"),"The dog is often called man's best friend.","Pes je často označován jako nejlepší přítel člověka."),
            q("ka003",1,Category.ANIMALS,"Which animal has a very long neck?","Které zvíře má velmi dlouhý krk?",listOf("giraffe"),listOf("zirafa","žirafa"),"A giraffe has a very long neck.","Žirafa má velmi dlouhý krk."),
            q("ka004",1,Category.ANIMALS,"Which animal has black and white stripes?","Které zvíře má černobílé pruhy?",listOf("zebra"),listOf("zebra"),"A zebra has black and white stripes.","Zebra má černobílé pruhy."),
            q("ka005",1,Category.ANIMALS,"Which animal carries its baby in a pouch?","Které zvíře nosí mládě ve vaku?",listOf("kangaroo"),listOf("klokan"),"A kangaroo carries its young in a pouch.","Klokan nosí mládě ve vaku."),
            q("ka006",1,Category.ANIMALS,"Which bird cannot fly and lives in Antarctica?","Který pták neumí létat a žije v Antarktidě?",listOf("penguin"),listOf("tucnak","tučňák"),"Penguins cannot fly and many live in Antarctica.","Tučňáci neumí létat a mnoho z nich žije v Antarktidě."),
            q("ka007",1,Category.ANIMALS,"What is the largest land animal?","Jaké je největší suchozemské zvíře?",listOf("elephant","african elephant"),listOf("slon","slon africky","slon africký"),"The African elephant is the largest land animal.","Slon africký je největší suchozemské zvíře."),
            q("ka008",1,Category.ANIMALS,"Which animal is famous for changing color to blend in?","Které zvíře je známé tím, že mění barvu a splývá s okolím?",listOf("chameleon"),listOf("chameleon"),"A chameleon can change its colors.","Chameleon umí měnit své zbarvení."),
            q("ka009",1,Category.ANIMALS,"How many legs does a spider have?","Kolik nohou má pavouk?",listOf("8","eight"),listOf("8","osm"),"A spider has eight legs.","Pavouk má osm nohou."),
            q("ka010",1,Category.ANIMALS,"Which sea animal has eight arms?","Který mořský živočich má osm ramen?",listOf("octopus"),listOf("chobotnice"),"An octopus has eight arms.","Chobotnice má osm ramen."),
            q("ka011",2,Category.ANIMALS,"Which mammal can truly fly?","Který savec umí skutečně létat?",listOf("bat"),listOf("netopyr","netopýr"),"Bats are mammals capable of true flight.","Netopýři jsou savci schopní skutečného letu."),
            q("ka012",2,Category.ANIMALS,"What do pandas mainly eat?","Co převážně jedí pandy?",listOf("bamboo"),listOf("bambus"),"Giant pandas mainly eat bamboo.","Pandy velké se živí hlavně bambusem."),
            q("ka013",2,Category.ANIMALS,"Which animal is the fastest on land?","Které zvíře je nejrychlejší na souši?",listOf("cheetah"),listOf("gepard"),"The cheetah is the fastest land animal.","Gepard je nejrychlejší suchozemské zvíře."),
            q("ka014",2,Category.ANIMALS,"What is a baby frog called?","Jak se anglicky říká larvě žáby?",listOf("tadpole"),listOf("pulec"),"A young frog before adulthood is a tadpole.","Larva žáby se česky nazývá pulec."),
            q("ka015",2,Category.ANIMALS,"Which animal builds dams in rivers?","Které zvíře staví hráze v řekách?",listOf("beaver"),listOf("bobr"),"Beavers build dams.","Bobři staví hráze."),
            q("ka016",2,Category.ANIMALS,"Which animal has a shell and can pull its head inside?","Které zvíře má krunýř a může do něj schovat hlavu?",listOf("turtle","tortoise"),listOf("zelva","želva"),"Turtles and tortoises have protective shells.","Želvy mají ochranný krunýř."),
            q("ka017",2,Category.ANIMALS,"What is the largest animal on Earth?","Jaké je největší zvíře na Zemi?",listOf("blue whale"),listOf("plejtvak obrovsky","plejtvák obrovský"),"The blue whale is the largest known animal.","Plejtvák obrovský je největší známé zvíře."),
            q("ka018",2,Category.ANIMALS,"Which animal sleeps hanging upside down?","Které zvíře často spí zavěšené hlavou dolů?",listOf("bat"),listOf("netopyr","netopýr"),"Many bats roost upside down.","Mnoho netopýrů odpočívá hlavou dolů."),
            q("ka019",3,Category.ANIMALS,"What type of animal is an axolotl?","Jaký druh živočicha je axolotl?",listOf("salamander","amphibian"),listOf("mlok","obojzivelnik","obojživelník"),"An axolotl is a salamander and an amphibian.","Axolotl je mlok a obojživelník."),
            q("ka020",3,Category.ANIMALS,"Which bird is famous for copying human speech?","Který pták je známý napodobováním lidské řeči?",listOf("parrot"),listOf("papousek","papoušek"),"Parrots are famous for mimicking sounds and speech.","Papoušci jsou známí napodobováním zvuků a řeči."),

            // Movies and characters
            q("km001",1,Category.MOVIES,"What is the name of the snowman in Frozen?","Jak se jmenuje sněhulák z Ledového království?",listOf("olaf"),listOf("olaf"),"The snowman's name is Olaf.","Sněhulák se jmenuje Olaf."),
            q("km002",1,Category.MOVIES,"Who is Simba's father in The Lion King?","Jak se jmenuje Simbův otec ve Lvím králi?",listOf("mufasa"),listOf("mufasa"),"Simba's father is Mufasa.","Simbův otec je Mufasa."),
            q("km003",1,Category.MOVIES,"What kind of fish is Nemo?","Jaký druh ryby je Nemo?",listOf("clownfish","clown fish"),listOf("klaun ocellaris","klaun","klauni ryba"),"Nemo is a clownfish.","Nemo je klaun očkatý."),
            q("km004",1,Category.MOVIES,"What is the name of the cowboy toy in Toy Story?","Jak se jmenuje kovbojská hračka v Toy Story?",listOf("woody"),listOf("woody"),"The cowboy is Woody.","Kovboj se jmenuje Woody."),
            q("km005",1,Category.MOVIES,"What is the name of the space ranger in Toy Story?","Jak se jmenuje vesmírný ranger v Toy Story?",listOf("buzz lightyear","buzz"),listOf("buzz raketak","buzz lightyear","buzz"),"The space ranger is Buzz Lightyear.","Vesmírný ranger je Buzz Rakeťák."),
            q("km006",1,Category.MOVIES,"Which superhero uses a shield with a star?","Který superhrdina používá štít s hvězdou?",listOf("captain america"),listOf("captain america","kapitan amerika"),"Captain America carries the star shield.","Štít s hvězdou používá Captain America."),
            q("km007",1,Category.MOVIES,"What is the name of Harry Potter's school?","Jak se jmenuje škola Harryho Pottera?",listOf("hogwarts"),listOf("bradavice","hogwarts"),"Harry attends Hogwarts.","Harry chodí do Bradavic."),
            q("km008",1,Category.MOVIES,"Which character lives in a pineapple under the sea?","Která postava žije v ananasu pod mořem?",listOf("spongebob","spongebob squarepants"),listOf("spongebob","spongebob v kalhotach"),"SpongeBob lives in a pineapple under the sea.","SpongeBob žije v ananasu pod mořem."),
            q("km009",1,Category.MOVIES,"What color is Lightning McQueen?","Jakou barvu má Blesk McQueen?",listOf("red"),listOf("cervena","červená"),"Lightning McQueen is red.","Blesk McQueen je červený."),
            q("km010",1,Category.MOVIES,"What is the name of the green ogre in Shrek?","Jak se jmenuje zelený zlobr ve filmu Shrek?",listOf("shrek"),listOf("shrek"),"The green ogre is Shrek.","Zelený zlobr se jmenuje Shrek."),
            q("km011",2,Category.MOVIES,"Who plays Iron Man in the Marvel movies?","Který herec hraje Iron Mana ve filmech Marvel?",listOf("robert downey junior","robert downey jr"),listOf("robert downey junior","robert downey jr"),"Robert Downey Jr. plays Iron Man.","Iron Mana hraje Robert Downey Jr."),
            q("km012",2,Category.MOVIES,"Who plays Jack Sparrow in Pirates of the Caribbean?","Který herec hraje Jacka Sparrowa v Pirátech z Karibiku?",listOf("johnny depp"),listOf("johnny depp"),"Johnny Depp plays Jack Sparrow.","Jacka Sparrowa hraje Johnny Depp."),
            q("km013",2,Category.MOVIES,"What is Darth Vader's relationship to Luke Skywalker?","Jaký vztah má Darth Vader k Luku Skywalkerovi?",listOf("father","his father"),listOf("otec","jeho otec"),"Darth Vader is Luke's father.","Darth Vader je Lukův otec."),
            q("km014",2,Category.MOVIES,"What type of animal is Po in Kung Fu Panda?","Jaké zvíře je Po z Kung Fu Pandy?",listOf("panda"),listOf("panda"),"Po is a giant panda.","Po je panda velká."),
            q("km015",2,Category.MOVIES,"Which actor plays Spider-Man in the Marvel Cinematic Universe movies such as Homecoming?","Který herec hraje Spider-Mana ve filmech Marvel jako Homecoming?",listOf("tom holland"),listOf("tom holland"),"Tom Holland plays Spider-Man in those films.","V těchto filmech hraje Spider-Mana Tom Holland."),
            q("km016",2,Category.MOVIES,"What is the name of the dragon in How to Train Your Dragon?","Jak se jmenuje drak v Jak vycvičit draka?",listOf("toothless"),listOf("bezzubka"),"The dragon is Toothless.","Drak se jmenuje Bezzubka."),
            q("km017",2,Category.MOVIES,"Which actor plays Willy Wonka in the 2005 movie Charlie and the Chocolate Factory?","Který herec hraje Willyho Wonku ve filmu Karlík a továrna na čokoládu z roku 2005?",listOf("johnny depp"),listOf("johnny depp"),"Johnny Depp plays Willy Wonka in that film.","Willyho Wonku v tomto filmu hraje Johnny Depp."),
            q("km018",3,Category.MOVIES,"Who directed Jurassic Park?","Kdo režíroval Jurský park?",listOf("steven spielberg"),listOf("steven spielberg"),"Steven Spielberg directed Jurassic Park.","Jurský park režíroval Steven Spielberg."),
            q("km019",3,Category.MOVIES,"Who directed the first Home Alone movie?","Kdo režíroval první film Sám doma?",listOf("chris columbus"),listOf("chris columbus"),"Chris Columbus directed the first Home Alone.","První film Sám doma režíroval Chris Columbus."),
            q("km020",3,Category.MOVIES,"Who voices Woody in the original English Toy Story movies?","Který herec namluvil Woodyho v původním anglickém znění Toy Story?",listOf("tom hanks"),listOf("tom hanks"),"Tom Hanks voices Woody.","Woodyho v angličtině namluvil Tom Hanks."),

            // Cars and transport
            q("kc001",1,Category.CARS,"How many wheels does a normal car usually have?","Kolik kol má běžné auto?",listOf("4","four"),listOf("4","ctyri","čtyři"),"A normal car has four wheels.","Běžné auto má čtyři kola."),
            q("kc002",1,Category.CARS,"Which part of a car do you turn to steer?","Čím v autě otáčíš, když chceš zatáčet?",listOf("steering wheel"),listOf("volant"),"You steer with the steering wheel.","Auto řídíš volantem."),
            q("kc003",1,Category.CARS,"What color usually means stop at a traffic light?","Která barva na semaforu znamená stůj?",listOf("red"),listOf("cervena","červená"),"Red means stop.","Červená znamená stůj."),
            q("kc004",1,Category.CARS,"Which vehicle travels on rails?","Které vozidlo jezdí po kolejích?",listOf("train","tram"),listOf("vlak","tramvaj"),"Trains and trams travel on rails.","Po kolejích jezdí vlak nebo tramvaj."),
            q("kc005",1,Category.CARS,"Which vehicle flies in the sky and has wings?","Který dopravní prostředek létá na obloze a má křídla?",listOf("airplane","plane"),listOf("letadlo"),"An airplane has wings and flies.","Letadlo má křídla a létá."),
            q("kc006",1,Category.CARS,"Which car brand uses a star with three points as its logo?","Která automobilka má ve znaku třícípou hvězdu?",listOf("mercedes","mercedes benz"),listOf("mercedes","mercedes benz"),"Mercedes-Benz uses the three-pointed star.","Mercedes-Benz používá třícípou hvězdu."),
            q("kc007",1,Category.CARS,"Which car brand has four rings in its logo?","Která automobilka má ve znaku čtyři kruhy?",listOf("audi"),listOf("audi"),"Audi uses four rings.","Audi má ve znaku čtyři kruhy."),
            q("kc008",1,Category.CARS,"Which car brand makes the Model 3?","Která automobilka vyrábí Model 3?",listOf("tesla"),listOf("tesla"),"Tesla makes the Model 3.","Model 3 vyrábí Tesla."),
            q("kc009",2,Category.CARS,"Which car brand makes the 911 sports car?","Která automobilka vyrábí sportovní model 911?",listOf("porsche"),listOf("porsche"),"Porsche makes the 911.","Model 911 vyrábí Porsche."),
            q("kc010",2,Category.CARS,"Which car brand uses a prancing horse as its symbol?","Která automobilka má ve znaku vzpínajícího se koně?",listOf("ferrari"),listOf("ferrari"),"Ferrari uses the prancing horse.","Ferrari používá znak vzpínajícího se koně."),
            q("kc011",2,Category.CARS,"Which is usually faster: a bicycle or a Formula One car?","Co je obvykle rychlejší: jízdní kolo, nebo Formule 1?",listOf("formula one car","formula one","f1 car"),listOf("formule 1","formule","f1"),"A Formula One car is much faster.","Formule 1 je mnohem rychlejší."),
            q("kc012",2,Category.CARS,"What powers a battery electric car?","Co pohání bateriový elektromobil?",listOf("electricity","battery","electric motor"),listOf("elektrina","baterie","elektromotor"),"An electric car uses electricity stored in a battery.","Elektromobil využívá elektřinu uloženou v baterii."),
            q("kc013",2,Category.CARS,"Which pedal usually makes a car slow down?","Který pedál auto obvykle zpomaluje?",listOf("brake","brake pedal"),listOf("brzda","brzdovy pedal","brzdový pedál"),"The brake pedal slows the car.","Auto zpomaluje brzdový pedál."),
            q("kc014",3,Category.CARS,"What does AWD mean on a car?","Co znamená u auta zkratka AWD?",listOf("all wheel drive","all-wheel drive"),listOf("pohon vsech kol","pohon všech kol","all wheel drive"),"AWD means all-wheel drive.","AWD znamená pohon všech kol."),
            q("kc015",3,Category.CARS,"Which is better for carrying many people: a two-seat sports car or a minivan?","Co je lepší pro převoz více lidí: dvoumístné sportovní auto, nebo minivan?",listOf("minivan","van"),listOf("minivan","dodavka","dodávka"),"A minivan has more passenger space.","Minivan má více místa pro cestující."),

            // Space
            q("ks001",1,Category.SPACE,"What planet do we live on?","Na které planetě žijeme?",listOf("earth"),listOf("zeme","země"),"We live on Earth.","Žijeme na Zemi."),
            q("ks002",1,Category.SPACE,"What is the name of Earth's natural satellite?","Jak se jmenuje přirozená družice Země?",listOf("moon"),listOf("mesic","měsíc"),"Earth's natural satellite is the Moon.","Přirozenou družicí Země je Měsíc."),
            q("ks003",1,Category.SPACE,"Which star gives Earth light and heat?","Která hvězda dává Zemi světlo a teplo?",listOf("sun"),listOf("slunce"),"The Sun gives Earth light and heat.","Slunce dává Zemi světlo a teplo."),
            q("ks004",1,Category.SPACE,"Which planet is known as the Red Planet?","Které planetě se říká Rudá planeta?",listOf("mars"),listOf("mars"),"Mars is called the Red Planet.","Mars se nazývá Rudá planeta."),
            q("ks005",1,Category.SPACE,"Which planet has famous rings?","Která planeta je známá svými prstenci?",listOf("saturn"),listOf("saturn"),"Saturn is famous for its rings.","Saturn je známý svými prstenci."),
            q("ks006",2,Category.SPACE,"What is the largest planet in our solar system?","Která planeta je největší ve Sluneční soustavě?",listOf("jupiter"),listOf("jupiter"),"Jupiter is the largest planet.","Jupiter je největší planeta."),
            q("ks007",2,Category.SPACE,"What do astronauts wear outside a spacecraft?","Co mají astronauti na sobě mimo kosmickou loď?",listOf("spacesuit","space suit"),listOf("skafandr"),"Astronauts wear spacesuits.","Astronauti nosí skafandry."),
            q("ks008",2,Category.SPACE,"What is a group of billions of stars called?","Jak se nazývá obrovská skupina miliard hvězd?",listOf("galaxy"),listOf("galaxie"),"A galaxy contains huge numbers of stars.","Galaxie obsahuje obrovské množství hvězd."),
            q("ks009",2,Category.SPACE,"What is the name of our galaxy?","Jak se jmenuje naše galaxie?",listOf("milky way"),listOf("mlecna draha","mléčná dráha"),"Our galaxy is the Milky Way.","Naše galaxie se jmenuje Mléčná dráha."),
            q("ks010",3,Category.SPACE,"What force keeps planets in orbit around the Sun?","Jaká síla udržuje planety na oběžných drahách kolem Slunce?",listOf("gravity"),listOf("gravitace"),"Gravity keeps planets in orbit.","Planety drží na oběžných drahách gravitace."),

            // Nature
            q("kn001",1,Category.NATURE,"What do plants need from the Sun to grow?","Co rostliny potřebují ze Slunce k růstu?",listOf("light","sunlight"),listOf("svetlo","slunecni svetlo","světlo"),"Plants use sunlight for photosynthesis.","Rostliny používají sluneční světlo při fotosyntéze."),
            q("kn002",1,Category.NATURE,"What falls from clouds when it rains?","Co padá z mraků, když prší?",listOf("water","rain"),listOf("voda","dest","déšť"),"Rain is water falling from clouds.","Déšť je voda padající z mraků."),
            q("kn003",1,Category.NATURE,"What season comes after spring?","Které roční období přichází po jaru?",listOf("summer"),listOf("leto","léto"),"Summer comes after spring.","Po jaru přichází léto."),
            q("kn004",1,Category.NATURE,"What season is usually the coldest?","Které roční období bývá nejchladnější?",listOf("winter"),listOf("zima"),"Winter is usually the coldest season.","Zima bývá nejchladnější období."),
            q("kn005",1,Category.NATURE,"What is frozen water called?","Jak se nazývá zmrzlá voda?",listOf("ice"),listOf("led"),"Frozen water is ice.","Zmrzlá voda je led."),
            q("kn006",2,Category.NATURE,"What gas do plants take from the air?","Který plyn rostliny přijímají ze vzduchu?",listOf("carbon dioxide","co2"),listOf("oxid uhlicity","oxid uhličitý","co2"),"Plants take in carbon dioxide.","Rostliny přijímají oxid uhličitý."),
            q("kn007",2,Category.NATURE,"What is water called when it turns into gas?","Jak se nazývá voda v plynném stavu?",listOf("water vapor","water vapour","vapor"),listOf("vodni para","vodní pára","para"),"Water as a gas is water vapor.","Voda v plynném stavu je vodní pára."),
            q("kn008",2,Category.NATURE,"What part of a plant usually grows underground?","Která část rostliny obvykle roste pod zemí?",listOf("root","roots"),listOf("koren","kořen","koreny","kořeny"),"Roots usually grow underground.","Kořeny obvykle rostou pod zemí."),
            q("kn009",2,Category.NATURE,"What do bees collect from flowers to help make honey?","Co včely sbírají z květů, aby mohly vyrábět med?",listOf("nectar"),listOf("nektar"),"Bees collect nectar from flowers.","Včely sbírají z květů nektar."),
            q("kn010",3,Category.NATURE,"What process lets plants use light to make food?","Jak se nazývá proces, při kterém rostliny využívají světlo k tvorbě živin?",listOf("photosynthesis"),listOf("fotosynteza","fotosyntéza"),"The process is photosynthesis.","Tento proces se nazývá fotosyntéza."),

            // Body
            q("kb001",1,Category.BODY,"Which organ pumps blood around your body?","Který orgán pumpuje krev do těla?",listOf("heart"),listOf("srdce"),"The heart pumps blood.","Srdce pumpuje krev."),
            q("kb002",1,Category.BODY,"Which body part do you use to smell?","Kterou část těla používáš k čichání?",listOf("nose"),listOf("nos"),"You smell with your nose.","Čicháš nosem."),
            q("kb003",1,Category.BODY,"How many eyes do most people have?","Kolik očí má většina lidí?",listOf("2","two"),listOf("2","dve","dvě"),"Most people have two eyes.","Většina lidí má dvě oči."),
            q("kb004",1,Category.BODY,"Which body part helps you hear?","Která část těla ti pomáhá slyšet?",listOf("ears","ear"),listOf("usi","uši","ucho"),"We hear with our ears.","Slyšíme ušima."),
            q("kb005",1,Category.BODY,"What covers most of the outside of your body?","Co pokrývá většinu povrchu lidského těla?",listOf("skin"),listOf("kuze","kůže"),"Skin covers the outside of your body.","Povrch těla pokrývá kůže."),
            q("kb006",2,Category.BODY,"Which organs help you breathe?","Které orgány ti pomáhají dýchat?",listOf("lungs"),listOf("plice"),"The lungs help you breathe.","Dýchání zajišťují plíce."),
            q("kb007",2,Category.BODY,"What protects your brain?","Co chrání mozek?",listOf("skull"),listOf("lebka"),"The skull protects the brain.","Mozek chrání lebka."),
            q("kb008",2,Category.BODY,"Which part of your body bends at the middle of your arm?","Který kloub se ohýbá uprostřed paže?",listOf("elbow"),listOf("loket"),"The elbow bends the arm.","Paže se ohýbá v lokti."),
            q("kb009",2,Category.BODY,"What do your muscles help you do?","S čím ti pomáhají svaly?",listOf("move","movement"),listOf("pohybovat se","pohyb"),"Muscles help your body move.","Svaly pomáhají tělu se pohybovat."),
            q("kb010",3,Category.BODY,"Which organ helps digest food after it leaves your mouth?","Který orgán pomáhá trávit jídlo poté, co ho spolkneš?",listOf("stomach"),listOf("zaludek","žaludek"),"The stomach helps digest food.","Žaludek pomáhá trávit potravu."),

            // Sports
            q("kp001",1,Category.SPORTS,"How many goals do you score if the ball goes into the soccer net once?","Kolik gólů získáš, když míč jednou skončí ve fotbalové brance?",listOf("1","one"),listOf("1","jeden"),"One ball in the net counts as one goal.","Jedna branka znamená jeden gól."),
            q("kp002",1,Category.SPORTS,"Which sport uses a racket and a yellow ball?","Který sport používá raketu a žlutý míček?",listOf("tennis"),listOf("tenis"),"Tennis uses rackets and a yellow ball.","Tenis používá rakety a žlutý míček."),
            q("kp003",1,Category.SPORTS,"Which sport is played on ice with a puck?","Který sport se hraje na ledě s pukem?",listOf("ice hockey","hockey"),listOf("hokej","ledni hokej"),"Ice hockey uses a puck.","Hokej se hraje s pukem."),
            q("kp004",1,Category.SPORTS,"In which sport do players try to shoot a ball through a hoop?","Ve kterém sportu se hráči snaží hodit míč do obroučky?",listOf("basketball"),listOf("basketbal"),"Basketball uses hoops.","V basketbalu se hází míč do koše."),
            q("kp005",1,Category.SPORTS,"Which sport uses a swimming pool?","Který sport se odehrává v bazénu?",listOf("swimming"),listOf("plavani","plavání"),"Swimming takes place in a pool.","Plavání se odehrává v bazénu."),
            q("kp006",2,Category.SPORTS,"How many players does one soccer team start with on the field?","S kolika hráči začíná jeden fotbalový tým na hřišti?",listOf("11","eleven"),listOf("11","jedenact","jedenáct"),"A soccer team starts with eleven players.","Fotbalový tým začíná s jedenácti hráči."),
            q("kp007",2,Category.SPORTS,"Which sport uses a shuttlecock?","Který sport používá košíček?",listOf("badminton"),listOf("badminton"),"Badminton uses a shuttlecock.","Badminton používá košíček."),
            q("kp008",2,Category.SPORTS,"How many points is a free throw worth in basketball?","Kolik bodů má trestný hod v basketbalu?",listOf("1","one"),listOf("1","jeden"),"A free throw is worth one point.","Trestný hod má hodnotu jednoho bodu."),
            q("kp009",2,Category.SPORTS,"What color card sends a player off in soccer?","Jaká karta ve fotbale znamená vyloučení?",listOf("red","red card"),listOf("cervena","červená","cervena karta"),"A red card sends a player off.","Červená karta znamená vyloučení."),
            q("kp010",3,Category.SPORTS,"Which sport has a position called goalkeeper and can also use hands only in a special area?","Ve kterém sportu má tým brankáře, který smí v pokutovém území používat ruce?",listOf("soccer","football"),listOf("fotbal"),"Soccer has a goalkeeper who can handle the ball in the penalty area.","Fotbal má brankáře, který smí v pokutovém území hrát rukama."),

            // Food and world
            q("kf001",1,Category.FOOD_WORLD,"Which fruit is yellow and curved?","Které ovoce je žluté a zahnuté?",listOf("banana"),listOf("banan","banán"),"A banana is yellow and curved.","Banán je žlutý a zahnutý."),
            q("kf002",1,Category.FOOD_WORLD,"Which vegetable is orange and rabbits are often shown eating it?","Která zelenina je oranžová a často se kreslí, že ji jedí králíci?",listOf("carrot"),listOf("mrkev"),"The answer is a carrot.","Odpověď je mrkev."),
            q("kf003",1,Category.FOOD_WORLD,"What do you get when water freezes?","Co vznikne, když voda zmrzne?",listOf("ice"),listOf("led"),"Frozen water becomes ice.","Zmrzlá voda se změní v led."),
            q("kf004",1,Category.FOOD_WORLD,"Which country is famous for pizza?","Která země je známá pizzou?",listOf("italy"),listOf("italie"),"Pizza is strongly associated with Italy.","Pizza je silně spojená s Itálií."),
            q("kf005",1,Category.FOOD_WORLD,"What is the capital of the Czech Republic?","Jaké je hlavní město České republiky?",listOf("prague"),listOf("praha"),"Prague is the capital of the Czech Republic.","Praha je hlavní město České republiky."),
            q("kf006",2,Category.FOOD_WORLD,"Which country has the Eiffel Tower?","Ve které zemi stojí Eiffelova věž?",listOf("france"),listOf("francie"),"The Eiffel Tower is in France.","Eiffelova věž stojí ve Francii."),
            q("kf007",2,Category.FOOD_WORLD,"Which country is shaped like a boot on maps?","Která země na mapě připomíná botu?",listOf("italy"),listOf("italie"),"Italy is often described as boot-shaped.","Itálie na mapě připomíná botu."),
            q("kf008",2,Category.FOOD_WORLD,"Which drink is made from cocoa and milk and is often served warm?","Který nápoj se vyrábí z kakaa a mléka a často se podává teplý?",listOf("hot chocolate","cocoa"),listOf("horka cokolada","horká čokoláda","kakao"),"Hot chocolate is made with cocoa and milk.","Horká čokoláda se připravuje z kakaa a mléka."),
            q("kf009",2,Category.FOOD_WORLD,"What food is made by baking dough and often has cheese and tomato on top?","Které jídlo se peče z těsta a často má nahoře sýr a rajčata?",listOf("pizza"),listOf("pizza"),"That food is pizza.","Je to pizza."),
            q("kf010",3,Category.FOOD_WORLD,"Which continent contains Egypt?","Na kterém kontinentu leží Egypt?",listOf("africa"),listOf("afrika"),"Egypt is in Africa.","Egypt leží v Africe."),

            // Logic and simple math
            q("kl001",1,Category.LOGIC,"What comes next: 2, 4, 6, 8?","Co následuje: 2, 4, 6, 8?",listOf("10","ten"),listOf("10","deset"),"The numbers increase by two, so ten comes next.","Čísla rostou po dvou, takže následuje deset."),
            q("kl002",1,Category.LOGIC,"If you have three apples and get two more, how many do you have?","Máš tři jablka a dostaneš další dvě. Kolik jich máš?",listOf("5","five"),listOf("5","pet","pět"),"Three plus two is five.","Tři plus dva je pět."),
            q("kl003",1,Category.LOGIC,"Which is heavier: one kilogram of feathers or one kilogram of stones?","Co je těžší: kilogram peří, nebo kilogram kamenů?",listOf("same","equal","they weigh the same"),listOf("stejne","stejně","vazi stejne","váží stejně"),"Both weigh one kilogram.","Obojí váží jeden kilogram."),
            q("kl004",1,Category.LOGIC,"How many sides does a triangle have?","Kolik stran má trojúhelník?",listOf("3","three"),listOf("3","tri","tři"),"A triangle has three sides.","Trojúhelník má tři strany."),
            q("kl005",1,Category.LOGIC,"What number is half of ten?","Kolik je polovina z deseti?",listOf("5","five"),listOf("5","pet","pět"),"Half of ten is five.","Polovina z deseti je pět."),
            q("kl006",2,Category.LOGIC,"If one car has four wheels, how many wheels do three cars have?","Když má jedno auto čtyři kola, kolik kol mají tři auta?",listOf("12","twelve"),listOf("12","dvanact","dvanáct"),"Three times four is twelve.","Tři krát čtyři je dvanáct."),
            q("kl007",2,Category.LOGIC,"What comes next: 5, 10, 15, 20?","Co následuje: 5, 10, 15, 20?",listOf("25","twenty five"),listOf("25","dvacet pet","dvacet pět"),"The pattern increases by five.","Řada se zvyšuje vždy o pět."),
            q("kl008",2,Category.LOGIC,"If you have twelve candies and share them equally between three children, how many does each get?","Máš dvanáct bonbonů a rozdělíš je stejně mezi tři děti. Kolik dostane každé?",listOf("4","four"),listOf("4","ctyri","čtyři"),"Twelve divided by three is four.","Dvanáct děleno třemi jsou čtyři."),
            q("kl009",2,Category.LOGIC,"Which is the odd one out: car, bus, bicycle, banana?","Co sem nepatří: auto, autobus, kolo, banán?",listOf("banana"),listOf("banan","banán"),"Banana is not a vehicle.","Banán není dopravní prostředek."),
            q("kl010",3,Category.LOGIC,"A farmer has ten sheep. Two walk away. How many remain?","Farmář má deset ovcí. Dvě odejdou. Kolik jich zbude?",listOf("8","eight"),listOf("8","osm"),"Ten minus two is eight.","Deset minus dva je osm."),
            q("kl011",3,Category.LOGIC,"What is 7 times 8?","Kolik je 7 krát 8?",listOf("56","fifty six"),listOf("56","padesat sest","padesát šest"),"Seven times eight is fifty-six.","Sedm krát osm je padesát šest."),
            q("kl012",3,Category.LOGIC,"What is 100 divided by 4?","Kolik je 100 děleno 4?",listOf("25","twenty five"),listOf("25","dvacet pet","dvacet pět"),"One hundred divided by four is twenty-five.","Sto děleno čtyřmi je dvacet pět."),
            q("kl013",3,Category.LOGIC,"If a movie starts at five and lasts two hours, when does it end?","Film začne v pět a trvá dvě hodiny. V kolik skončí?",listOf("7","seven","7 o'clock"),listOf("7","sedm","v sedm"),"It ends at seven.","Skončí v sedm."),
            q("kl014",3,Category.LOGIC,"Which number is larger: 0.5 or 0.8?","Které číslo je větší: 0,5 nebo 0,8?",listOf("0.8","zero point eight"),listOf("0.8","0,8","nula cela osm"),"Zero point eight is larger.","Nula celá osm je větší."),
            q("kl015",3,Category.LOGIC,"If you double 15, what number do you get?","Když zdvojnásobíš 15, kolik dostaneš?",listOf("30","thirty"),listOf("30","tricet","třicet"),"Double fifteen is thirty.","Dvojnásobek patnácti je třicet.")
        )


        private val questions: List<Question>
            get() = LiveContentRepository.kidsGameQuestions().ifEmpty { bundledQuestions }
    }
}
