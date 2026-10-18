package org.nulllauncher.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import org.nulllauncher.mobile.ui.theme.Line
import org.nulllauncher.mobile.ui.theme.Mute

/**
 * Политика конфиденциальности внутри приложения (требование RuStore:
 * политика должна быть доступна и в карточке, и в самом продукте).
 * Полный текст синхронизирован с docs/privacy.html.
 */
@Composable
fun PrivacyScreen(nav: NavController) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Mute)
            }
            Text("Конфиденциальность", style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Политика конфиденциальности · действует с 18.10.2026",
                fontSize = 11.sp, color = Mute, modifier = Modifier.padding(bottom = 12.dp),
            )

            Section("1. Какие данные обрабатываются") {
                Text(
                    "• Данные учётной записи Microsoft / Xbox — токены доступа и идентификатор профиля. " +
                        "Используются только для официального входа в игру (device-code). Пароль в приложение " +
                        "никогда не передаётся и не хранится.",
                    fontSize = 13.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Технические данные о вылетах — модель устройства, версия Android и стек ошибки. " +
                        "Записываются локально в журнал для диагностики и не отправляются третьим лицам.",
                    fontSize = 13.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Данные о подписке — статус и срок Premium. Оплата через RuStore Pay; " +
                        "платёжные данные приложению недоступны.",
                    fontSize = 13.5.sp,
                )
            }

            Section("2. Какие данные НЕ собираются") {
                Text(
                    "• Пароли от учётных записей.\n" +
                        "• Геоданные, контакты, файлы пользователя (миры и сборки остаются на устройстве).\n" +
                        "• Реклама и аналитика: в приложении нет рекламных и аналитических SDK и трекеров.",
                    fontSize = 13.5.sp,
                )
            }

            Section("3. Цели обработки") {
                Text(
                    "Авторизация в игре, диагностика сбоев, обеспечение работы купленной подписки. " +
                        "Правовое основание — исполнение договора с пользователем (ФЗ № 152-ФЗ).",
                    fontSize = 13.5.sp,
                )
            }

            Section("4. Хранение и защита") {
                Text(
                    "Все данные хранятся локально на устройстве и не передаются на серверы разработчика. " +
                        "Токены хранятся в защищённом хранилище приложения.",
                    fontSize = 13.5.sp,
                )
            }

            Section("5. Сторонние сервисы") {
                Text(
                    "Microsoft / Xbox (авторизация), сервисы профиля Mojang (вход в игру), RuStore Pay " +
                        "(платежи), Modrinth (открытый каталог модов — запросы только по вашему действию).",
                    fontSize = 13.5.sp,
                )
            }

            Section("6. Как удалить данные") {
                Text(
                    "• Профиль → «Выйти из аккаунта» — удалить токены и профиль Microsoft с устройства.\n" +
                        "• Настройки Android → Приложения → nullLauncher Mobile → «Удалить данные» — " +
                        "полная очистка, включая журналы и сборки.\n" +
                        "• Вопросы: Telegram @rustywvv.",
                    fontSize = 13.5.sp,
                )
            }

            Section("7. Дети") {
                Text(
                    "Приложение не собирает данные детей. Используется официальная авторизация Microsoft, " +
                        "где возрастной учёт задаётся родителем на стороне Microsoft.",
                    fontSize = 13.5.sp,
                )
            }

            Section("8. Изменения") {
                Text(
                    "Новая редакция публикуется в приложении и на сайте проекта с указанием даты.",
                    fontSize = 13.5.sp,
                )
            }

            Text(
                "nullLauncher Mobile · не связан с Mojang Studios и Microsoft",
                fontSize = 11.sp, color = Mute, modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Line)
    }
}
