package com.mamoru.service

import com.google.genai.Client
import com.mamoru.LinkFixerBot
import com.mamoru.repository.ChatJpaRepository
import com.mamoru.repository.ChatRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random


@Service
class ScheduledMessageService(
    private val linkFixerBot: LinkFixerBot,
    private val chatRepository: ChatRepository,
    private val chatJpaRepository: ChatJpaRepository,
    @Value("\${scheduled.message.text:Daily reminder: I'm here to fix your links!}")
    private val scheduledMessageText: String,
    private val videoCacheService: VideoCacheService

) {

    private val logger = LoggerFactory.getLogger(ScheduledMessageService::class.java)

    private val scheduledMessages = arrayListOf(
        "До перемоги України у війні над Росією залишилося всього %s днів. Цей день стане новою сторінкою в історії нашої країни та усього вільного світу.",
        "Через %s днів завершиться одна з найтрагічніших сторінок в історії України, і над країною запанує мир, освячений нашою перемогою над агресором.",
        "Кожен із цих %s днів наближає нас до моменту, коли Україна остаточно звільниться від гніту та насильства з боку агресора. Ми на порозі великої перемоги!",
        "Вже зовсім скоро, через %s днів, Україна поставить крапку в цій війні, продемонструвавши всьому світу свою силу, стійкість та прагнення до свободи.",
        "Залишилося всього %s днів до того моменту, коли український народ з гордістю скаже: ми перемогли, ми вистояли, ми вільні!",
        "Кожен день боротьби наближає нас до перемоги. Всього через %s днів Україна святкуватиме довгоочікуваний мир, здобутий ціною величезних зусиль.",
        "Через %s днів Україна покаже всьому світу, що сила духу, єдність та правда здатні перемогти будь-яку агресію. Цей день вже близько!",
        "Всього %s днів відділяють нас від моменту, коли світ побачить: Україна не лише вистояла, але й здобула тріумфальну перемогу в цій жорстокій війні.",
        "Коли минуть ці %s днів, Україна остаточно утвердить свою свободу, і весь світ захоплюватиметься стійкістю нашого народу. Перемога вже на горизонті!",
        "Залишилося всього %s днів, щоб над Україною знову засяяло сонце миру та свободи, ознаменувавши перемогу над ворогом і повернення спокійного життя.",
        "Через %s днів ми зможемо сказати: наша боротьба увінчалася перемогою, і майбутнє України стало світлим і вільним.",
        "Останні %s днів війни — це час, коли український народ демонструє свою незламність, готуючись до перемоги.",
        "Ще трохи терпіння, ще %s днів — і Україна відсвяткує тріумфальне завершення війни.",
        "Ми стоїмо на порозі великого дня, який настане через %s днів і стане символом перемоги України.",
        "Через %s днів весь світ стане свідком того, як Україна здобула заслужену перемогу в боротьбі за свободу.",
        "Залишилося всього %s днів до того моменту, коли ми зможемо пишатися здобутою перемогою і мирним життям.",
        "Ще %s днів — і Україна увійде в нову еру миру та відновлення після великої перемоги над агресором.",
        "Кожен день наближає нас до перемоги. Ще %s днів, і мрія про вільну Україну стане реальністю.",
        "Україна вже на шляху до перемоги. Через %s днів ми завершимо цю війну на нашу користь.",
        "Через %s днів ми святкуватимемо перемогу, яка стане доказом сили, єдності та мужності українського народу.",
        "Ще %s днів, і Україна відновить мир та свободу, які були несправедливо порушені.",
        "Через %s днів ми разом відзначимо перемогу, яка стала результатом нашої спільної боротьби.",
        "Останні %s днів війни — це час, коли наша країна демонструє незламну віру в перемогу.",
        "Ще %s днів, і мирне небо над Україною стане символом нашої перемоги та свободи.",
        "Через %s днів український народ відзначатиме день, який стане початком нового етапу в історії країни.",
        "Ми на порозі історичного моменту. Ще %s днів — і Україна здобуде заслужену перемогу.",
        "Кожен день боротьби наближає нас до перемоги. Ще %s днів терпіння та віри.",
        "Україна переможе через %s днів, і ця перемога стане тріумфом добра над злом.",
        "Залишилося всього %s днів до моменту, коли ми зможемо зітхнути з полегшенням і сказати: ми перемогли.",
        "Через %s днів ми побачимо кінець війни і початок нової мирної епохи для України.",
        "Ще трохи, ще %s днів — і Україна переможе, відновивши свою незалежність і територіальну цілісність.",
        "Через %s днів ми зможемо з гордістю сказати: ми вистояли, ми перемогли, ми вільні.",
        "Кожен день боротьби — це ще один крок до перемоги. Залишилося всього %s днів.",
        "Останні %s днів війни — це час, коли віра в перемогу стає ще сильнішою.",
        "Ще трохи, ще %s днів — і Україна відзначатиме день перемоги над агресором.",
        "Через %s днів український прапор гордо майорітиме над звільненими територіями.",
        "Залишилося всього %s днів до того, як мирне життя повернеться в кожен український дім.",
        "Ми стоїмо на порозі великої перемоги. Ще %s днів боротьби та віри.",
        "Через %s днів український народ святкуватиме свободу, здобуту ціною величезних зусиль.",
        "Ще трохи терпіння — через %s днів наша перемога стане реальністю.",
        "Україна вже на шляху до тріумфу. Залишилося всього %s днів до перемоги.",
        "Через %s днів ми відзначимо день, який увійде в історію як день перемоги над агресором.",
        "Останні %s днів війни — це час, коли віра в мир і свободу надихає нас.",
        "Ще %s днів, і Україна стане прикладом незламності для всього світу.",
        "Через %s днів наша боротьба завершиться перемогою, яка змінить хід історії.",
        "Залишилося всього %s днів до моменту, коли мир і спокій повернуться в Україну."
    )

    private val targetDate = LocalDate.of(2025, 5, 2)

    @Scheduled(cron = "0 15 2 * * *")
    fun cleanCache() {
        logger.info("Starting scheduled cache cleanup")
        videoCacheService.cleanCache()
        deleteFilesFromDirectory("/data/downloads")
        logger.info("Completed scheduled cache cleanup")
    }

    // Schedule for 13:15 every day
    @Scheduled(cron = "0 15 11 * * *")
//    @Scheduled(cron = "0 34 20 * * *")
    fun sendDailyMessage() {
        logger.info("Starting scheduled daily message sending")
//        val chats = chatRepository.getAllChats()
        val chatsP = chatJpaRepository.findAll();
        if (chatsP.isEmpty()) {
            logger.info("No active chats found to send the scheduled message")
            return
        }

        val today = LocalDate.now()
        val daysUntilTarget = today.until(targetDate, ChronoUnit.DAYS)

        // Choose a random message template
        val messageTemplate = scheduledMessages[Random.nextInt(scheduledMessages.size)]

        // Format the message with the days count
        val formattedMessage = String.format(messageTemplate, daysUntilTarget)

        logger.info("Sending scheduled message to ${chatsP.size} chats. Days until target: $daysUntilTarget")
        for (chat in chatsP) {
            if (chat.sendCounterUntilWin) {
                linkFixerBot.sendMessageToChat(chat.chatId, formattedMessage)
                logger.info("Sent scheduled message to chat ${chat.chatId}")
            }
        }

        logger.info("Completed scheduled daily message sending")
    }

    @Scheduled(cron = "0 15 14 * * *")
//    @Scheduled(cron = "0 34 20 * * *")
    fun sendDailyJokeMessage() {
        logger.info("Starting scheduled daily joke message sending")
//        val chats = chatRepository.getAllChats()
        val chatsP = chatJpaRepository.findAll();
        if (chatsP.isEmpty()) {
            logger.info("No active chats found to send the scheduled message")
            return
        }

//        logger.info("Sending scheduled message to ${chatsP.size} chats. Days until target: $daysUntilTarget")
        for (chat in chatsP) {
            if (chat.sendRandomJoke) {
                linkFixerBot.sendMessageToChat(chat.chatId, linkFixerBot.getRandomJoke())
                logger.info("Sent scheduled message with joke to chat ${chat.chatId}")
            }
        }

        logger.info("Completed scheduled daily message sending")
    }

//    fun getRandomJoke(): String {
////        TODO("Not yet implemented")
//        val client = Client()
//
//        val response =
//            client.models.generateContent("gemini-2.0-flash-001", "Ти - Лідер України, Володимир Зеленський, роскажи актуальну шутку", null)
//
//        return response.text() ?: "Вибач, я шутку не придумав";
//    }

    private fun deleteFilesFromDirectory(directoryPath: String) {
        val directory = File(directoryPath)
        if (directory.exists() && directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (file.delete()) {
                        logger.info("Deleted file: ${file.absolutePath}")
                    } else {
                        logger.warn("Failed to delete file: ${file.absolutePath}")
                    }
                }
            }
        } else {
            logger.warn("Directory does not exist or is not a directory: $directoryPath")
        }
    }
}

