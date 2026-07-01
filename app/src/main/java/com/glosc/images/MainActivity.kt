package com.glosc.images

import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.glosc.images.core.common.UiState
import com.glosc.images.core.ui.Design
import com.glosc.images.core.ui.addGap
import com.glosc.images.core.ui.addSpaced
import com.glosc.images.core.ui.artPlaceholder
import com.glosc.images.core.ui.bodyText
import com.glosc.images.core.ui.card
import com.glosc.images.core.ui.chip
import com.glosc.images.core.ui.column
import com.glosc.images.core.ui.dangerButton
import com.glosc.images.core.ui.dashedBg
import com.glosc.images.core.ui.dp
import com.glosc.images.core.ui.ghostButton
import com.glosc.images.core.ui.input
import com.glosc.images.core.ui.label
import com.glosc.images.core.ui.loadAsset
import com.glosc.images.core.ui.mono
import com.glosc.images.core.ui.primaryButton
import com.glosc.images.core.ui.roundedBg
import com.glosc.images.core.ui.row
import com.glosc.images.core.ui.title
import com.glosc.images.domain.model.ApiProvider
import com.glosc.images.domain.model.AppUpdateInfo
import com.glosc.images.domain.model.AppUpdateStatus
import com.glosc.images.domain.model.GenerateImageRequest
import com.glosc.images.domain.model.GenerationTask
import com.glosc.images.domain.model.ImageAsset
import com.glosc.images.domain.model.ProviderType
import com.glosc.images.domain.model.SourceType
import com.glosc.images.domain.model.TaskStatus
import com.glosc.images.ui.AppScreen
import com.glosc.images.ui.MainViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

private const val MAX_SOURCE_IMAGES = 16
private const val PREFS_NAME = "ui_preferences"
private const val PREF_LANGUAGE = "language"
private const val DEFAULT_PROMPT_ZH = "一只机械蜂鸟悬停在发光的玻璃花朵旁，微距摄影，冷蓝色调，体积光，超精细细节"
private const val DEFAULT_PROMPT_EN = "A mechanical hummingbird hovering beside a glowing glass flower, macro photography, cool blue tones, volumetric light, ultra-fine detail"

private enum class StudioMode {
    TextToImage,
    ImageToImage
}

private enum class LanguageMode(
    val key: String,
    val nativeName: String,
    val shortLabel: String,
    val locale: Locale,
    val rtl: Boolean = false
) {
    Auto("auto", "Auto", "Auto", Locale.US),
    English("en", "English", "EN", Locale.US),
    Chinese("zh", "简体中文", "中", Locale.CHINA),
    Hindi("hi", "हिन्दी", "HI", Locale.forLanguageTag("hi-IN")),
    Spanish("es", "Español", "ES", Locale.forLanguageTag("es-ES")),
    French("fr", "Français", "FR", Locale.FRANCE),
    Arabic("ar", "العربية", "AR", Locale.forLanguageTag("ar"), rtl = true),
    Bengali("bn", "বাংলা", "BN", Locale.forLanguageTag("bn-BD")),
    Portuguese("pt", "Português", "PT", Locale.forLanguageTag("pt-BR")),
    Russian("ru", "Русский", "RU", Locale.forLanguageTag("ru-RU")),
    Urdu("ur", "اردو", "UR", Locale.forLanguageTag("ur-PK"), rtl = true);

    companion object {
        fun fromKey(key: String?): LanguageMode =
            entries.firstOrNull { it.key == key } ?: Auto
    }
}

private val LANGUAGE_TRANSLATIONS = mapOf(
    LanguageMode.Hindi to mapOf(
        DEFAULT_PROMPT_EN to "चमकते कांच के फूल के पास मंडराता यांत्रिक हमिंगबर्ड, मैक्रो फोटोग्राफी, ठंडे नीले रंग, वॉल्यूमेट्रिक लाइट, बेहद बारीक विवरण",
        "Follow system" to "सिस्टम के अनुसार",
        "Language" to "भाषा",
        "Cancel" to "रद्द करें",
        "Text to Image" to "टेक्स्ट से इमेज",
        "Image to Image" to "इमेज से इमेज",
        "Chat" to "चैट",
        "Edit" to "संपादित करें",
        "Transform" to "रूपांतरण",
        "First Launch" to "पहला लॉन्च",
        "Setup Guide" to "सेटअप गाइड",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "इमेज बनाना शुरू करने के लिए सेटअप पूरा करें: अपनी Glosc AI key सेव करें, इमेज मॉडल लाएं, और डिफॉल्ट मॉडल चुनें।",
        "1. Connect Glosc AI" to "1. Glosc AI कनेक्ट करें",
        "Channel" to "चैनल",
        "2. Fetch Image Models" to "2. इमेज मॉडल लाएं",
        "The app calls /v1/models and keeps only models whose categories include image." to "ऐप /v1/models कॉल करता है और केवल image श्रेणी वाले मॉडल रखता है।",
        "No image models fetched yet" to "अभी कोई इमेज मॉडल नहीं लाया गया",
        "Save Key and Fetch Models" to "Key सेव करें और मॉडल लाएं",
        "3. Choose Default Image Model" to "3. डिफॉल्ट इमेज मॉडल चुनें",
        "Default Model" to "डिफॉल्ट मॉडल",
        "Text-to-image and image-to-image will use this model by default." to "टेक्स्ट-टू-इमेज और इमेज-टू-इमेज डिफॉल्ट रूप से इसी मॉडल का उपयोग करेंगे।",
        "Start Creating" to "बनाना शुरू करें",
        "Glosc One image models ready" to "Glosc One इमेज मॉडल तैयार हैं",
        "AI Studio" to "AI स्टूडियो",
        "IMAGE TOOLS" to "इमेज टूल्स",
        "Model" to "मॉडल",
        "Source Images" to "स्रोत इमेज",
        "Prompt" to "प्रॉम्प्ट",
        "Describe how you want to transform the images..." to "बताएं कि आप इमेज को कैसे बदलना चाहते हैं...",
        "Describe the image you want to generate..." to "आप कैसी इमेज बनाना चाहते हैं, लिखें...",
        "Image Size" to "इमेज आकार",
        "Resolution" to "रिजॉल्यूशन",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "ऑटो अनुपात 1K का उपयोग करता है। चुना गया Glosc One मॉडल समर्थन बताए तब तक उच्च रिजॉल्यूशन बंद रहेंगे।",
        "Generating..." to "बन रहा है...",
        "Generate" to "जनरेट करें",
        "Failed to read reference image" to "संदर्भ इमेज पढ़ने में विफल",
        "Generated Images" to "जनरेट की गई इमेज",
        "Generating with Glosc One..." to "Glosc One से जनरेट हो रहा है...",
        "No images generated yet" to "अभी कोई इमेज जनरेट नहीं हुई",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "अपनी पहली AI इमेज बनाने के लिए प्रॉम्प्ट लिखें। परिणाम यहां दिखेंगे।",
        "Upload images" to "इमेज अपलोड करें",
        "Tap to select reference images" to "संदर्भ इमेज चुनने के लिए टैप करें",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "JPG, PNG, GIF, WebP समर्थित · अधिकतम 16 इमेज",
        "Clear" to "साफ करें",
        "auto" to "ऑटो",
        "Please upload an image and enter a prompt to generate" to "कृपया इमेज अपलोड करें और प्रॉम्प्ट लिखें",
        "Please upload an image to generate" to "कृपया पहले इमेज अपलोड करें",
        "Please enter a prompt to generate" to "कृपया प्रॉम्प्ट लिखें",
        "Ready to generate" to "जनरेट करने के लिए तैयार",
        "Please upload reference images first" to "कृपया पहले संदर्भ इमेज अपलोड करें",
        "Unable to read reference image" to "संदर्भ इमेज पढ़ी नहीं जा सकी",
        "Negative" to "नेगेटिव",
        "Tags" to "टैग",
        "Info" to "जानकारी",
        "Export" to "निर्यात",
        "Delete" to "हटाएं",
        "Delete this image? This will remove the local file and database record." to "यह इमेज हटाएं? इससे स्थानीय फाइल और डेटाबेस रिकॉर्ड हट जाएंगे।",
        "Originals are preserved · Edits save as new images" to "मूल इमेज सुरक्षित रहती हैं · एडिट नई इमेज के रूप में सेव होते हैं",
        "API Settings" to "API सेटिंग्स",
        "Studio" to "स्टूडियो",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "इस संस्करण में केवल टेक्स्ट से इमेज, इमेज से इमेज, और Glosc One मॉडल कनेक्शन रखा गया है।",
        "Fetch Models" to "मॉडल लाएं",
        "Save" to "सेव करें",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "मॉडल सूची /v1/models से आती है। केवल image श्रेणी वाले मॉडल उपयोग होते हैं। API keys Android Keystore से एन्क्रिप्ट होती हैं।",
        "‹ Back" to "‹ वापस",
        "Share" to "शेयर",
        "Use the system share sheet to send this image" to "इस इमेज को भेजने के लिए सिस्टम शेयर शीट का उपयोग करें",
        "Inpaint" to "इनपेंट",
        "Variation" to "वैरिएशन",
        "Upscale" to "अपस्केल",
        "Outpaint" to "आउटपेंट",
        "+ Add" to "+ जोड़ें",
        "Add Tag" to "टैग जोड़ें",
        "Source" to "स्रोत",
        "Size" to "आकार",
        "Seed" to "सीड",
        "Random" to "रैंडम",
        "Created" to "बनाया गया",
        "Storage" to "स्टोरेज",
        "Sample placeholder" to "सैंपल प्लेसहोल्डर",
        "Local file" to "स्थानीय फाइल",
        "Processing..." to "प्रोसेस हो रहा है...",
        "Fetch models first" to "पहले मॉडल लाएं",
        "This sample has no local file to export" to "इस सैंपल में निर्यात के लिए स्थानीय फाइल नहीं है",
        "Export Image" to "इमेज निर्यात करें",
        "No image model fetched" to "कोई इमेज मॉडल नहीं लाया गया",
        "here" to "यहां",
        "Get your key from here" to "अपनी key यहां से लें",
        "Example: change the background to a bright day and keep the subject pose" to "उदाहरण: बैकग्राउंड को उजले दिन में बदलें और विषय की मुद्रा रखें",
        "Make the mood colder and add light snow" to "माहौल ठंडा करें और हल्की बर्फ जोड़ें",
        "Describe what to change and the target look. A new image will be generated." to "क्या बदलना है और लक्ष्य रूप कैसा हो, बताएं। एक नई इमेज बनेगी।",
        "Apply and Generate" to "लागू कर जनरेट करें",
        "Confirm" to "पुष्टि करें",
        "Add" to "जोड़ें"
    ),
    LanguageMode.Spanish to mapOf(
        DEFAULT_PROMPT_EN to "Un colibrí mecánico flotando junto a una flor de vidrio luminosa, fotografía macro, tonos azules fríos, luz volumétrica, detalles ultrafinos",
        "Follow system" to "Seguir sistema",
        "Language" to "Idioma",
        "Cancel" to "Cancelar",
        "Text to Image" to "Texto a imagen",
        "Image to Image" to "Imagen a imagen",
        "Chat" to "Chat",
        "Edit" to "Editar",
        "Transform" to "Transformar",
        "First Launch" to "Primer inicio",
        "Setup Guide" to "Guía de configuración",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "Completa la configuración para crear imágenes: guarda tu clave de Glosc AI, carga los modelos de imagen y elige un modelo predeterminado.",
        "1. Connect Glosc AI" to "1. Conectar Glosc AI",
        "Channel" to "Canal",
        "2. Fetch Image Models" to "2. Cargar modelos de imagen",
        "The app calls /v1/models and keeps only models whose categories include image." to "La app llama a /v1/models y conserva solo los modelos cuya categoría incluye image.",
        "No image models fetched yet" to "Aún no hay modelos cargados",
        "Save Key and Fetch Models" to "Guardar clave y cargar modelos",
        "3. Choose Default Image Model" to "3. Elegir modelo predeterminado",
        "Default Model" to "Modelo predeterminado",
        "Text-to-image and image-to-image will use this model by default." to "Texto a imagen e imagen a imagen usarán este modelo por defecto.",
        "Start Creating" to "Empezar a crear",
        "Glosc One image models ready" to "Modelos de imagen de Glosc One listos",
        "AI Studio" to "Estudio AI",
        "IMAGE TOOLS" to "HERRAMIENTAS",
        "Model" to "Modelo",
        "Source Images" to "Imágenes fuente",
        "Prompt" to "Prompt",
        "Describe how you want to transform the images..." to "Describe cómo quieres transformar las imágenes...",
        "Describe the image you want to generate..." to "Describe la imagen que quieres generar...",
        "Image Size" to "Tamaño",
        "Resolution" to "Resolución",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "La proporción automática usa 1K. Las resoluciones superiores quedan desactivadas hasta que el modelo de Glosc One indique soporte.",
        "Generating..." to "Generando...",
        "Generate" to "Generar",
        "Failed to read reference image" to "No se pudo leer la imagen de referencia",
        "Generated Images" to "Imágenes generadas",
        "Generating with Glosc One..." to "Generando con Glosc One...",
        "No images generated yet" to "Aún no hay imágenes generadas",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "Escribe un prompt para generar tu primera imagen AI. Los resultados aparecerán aquí.",
        "Upload images" to "Subir imágenes",
        "Tap to select reference images" to "Toca para seleccionar imágenes de referencia",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "Admite JPG, PNG, GIF, WebP · Máx. 16 imágenes",
        "Clear" to "Limpiar",
        "auto" to "auto",
        "Please upload an image and enter a prompt to generate" to "Sube una imagen y escribe un prompt",
        "Please upload an image to generate" to "Sube una imagen para generar",
        "Please enter a prompt to generate" to "Escribe un prompt para generar",
        "Ready to generate" to "Listo para generar",
        "Please upload reference images first" to "Sube primero imágenes de referencia",
        "Unable to read reference image" to "No se pudo leer la imagen de referencia",
        "Negative" to "Negativo",
        "Tags" to "Etiquetas",
        "Info" to "Información",
        "Export" to "Exportar",
        "Delete" to "Eliminar",
        "Delete this image? This will remove the local file and database record." to "¿Eliminar esta imagen? Se borrará el archivo local y el registro de la base de datos.",
        "Originals are preserved · Edits save as new images" to "Los originales se conservan · Las ediciones se guardan como nuevas imágenes",
        "API Settings" to "Configuración API",
        "Studio" to "Estudio",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "Esta versión conserva solo Texto a imagen, Imagen a imagen y la conexión con Glosc One.",
        "Fetch Models" to "Cargar modelos",
        "Save" to "Guardar",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "Las listas vienen de /v1/models. Solo se usan modelos con categoría image. Las claves API se cifran con Android Keystore.",
        "‹ Back" to "‹ Volver",
        "Share" to "Compartir",
        "Use the system share sheet to send this image" to "Usa el panel de compartir del sistema para enviar esta imagen",
        "Inpaint" to "Rellenar",
        "Variation" to "Variación",
        "Upscale" to "Escalar",
        "Outpaint" to "Expandir",
        "+ Add" to "+ Añadir",
        "Add Tag" to "Añadir etiqueta",
        "Source" to "Fuente",
        "Size" to "Tamaño",
        "Seed" to "Semilla",
        "Random" to "Aleatorio",
        "Created" to "Creado",
        "Storage" to "Almacenamiento",
        "Sample placeholder" to "Marcador de ejemplo",
        "Local file" to "Archivo local",
        "Processing..." to "Procesando...",
        "Fetch models first" to "Carga modelos primero",
        "This sample has no local file to export" to "Esta muestra no tiene archivo local para exportar",
        "Export Image" to "Exportar imagen",
        "No image model fetched" to "No hay modelo de imagen cargado",
        "here" to "aquí",
        "Get your key from here" to "Obtén tu clave aquí",
        "Example: change the background to a bright day and keep the subject pose" to "Ejemplo: cambia el fondo a un día luminoso y conserva la pose",
        "Make the mood colder and add light snow" to "Haz el ambiente más frío y añade nieve ligera",
        "Describe what to change and the target look. A new image will be generated." to "Describe qué cambiar y el aspecto deseado. Se generará una nueva imagen.",
        "Apply and Generate" to "Aplicar y generar",
        "Confirm" to "Confirmar",
        "Add" to "Añadir"
    ),
    LanguageMode.French to mapOf(
        DEFAULT_PROMPT_EN to "Un colibri mécanique flottant près d'une fleur de verre lumineuse, photographie macro, tons bleus froids, lumière volumétrique, détails ultrafins",
        "Follow system" to "Suivre le système",
        "Language" to "Langue",
        "Cancel" to "Annuler",
        "Text to Image" to "Texte en image",
        "Image to Image" to "Image en image",
        "Chat" to "Chat",
        "Edit" to "Modifier",
        "Transform" to "Transformer",
        "First Launch" to "Premier lancement",
        "Setup Guide" to "Guide de configuration",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "Terminez la configuration pour créer des images : enregistrez votre clé Glosc AI, récupérez les modèles d'image et choisissez un modèle par défaut.",
        "1. Connect Glosc AI" to "1. Connecter Glosc AI",
        "Channel" to "Canal",
        "2. Fetch Image Models" to "2. Récupérer les modèles d'image",
        "The app calls /v1/models and keeps only models whose categories include image." to "L'application appelle /v1/models et conserve uniquement les modèles dont les catégories incluent image.",
        "No image models fetched yet" to "Aucun modèle d'image récupéré",
        "Save Key and Fetch Models" to "Enregistrer la clé et récupérer les modèles",
        "3. Choose Default Image Model" to "3. Choisir le modèle d'image par défaut",
        "Default Model" to "Modèle par défaut",
        "Text-to-image and image-to-image will use this model by default." to "Texte en image et image en image utiliseront ce modèle par défaut.",
        "Start Creating" to "Commencer à créer",
        "Glosc One image models ready" to "Modèles d'image Glosc One prêts",
        "AI Studio" to "Studio IA",
        "IMAGE TOOLS" to "OUTILS IMAGE",
        "Model" to "Modèle",
        "Source Images" to "Images source",
        "Prompt" to "Prompt",
        "Describe how you want to transform the images..." to "Décrivez comment transformer les images...",
        "Describe the image you want to generate..." to "Décrivez l'image à générer...",
        "Image Size" to "Taille de l'image",
        "Resolution" to "Résolution",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "Le ratio auto utilise 1K. Les résolutions supérieures restent désactivées jusqu'à ce que le modèle Glosc One choisi indique leur prise en charge.",
        "Generating..." to "Génération...",
        "Generate" to "Générer",
        "Failed to read reference image" to "Impossible de lire l'image de référence",
        "Generated Images" to "Images générées",
        "Generating with Glosc One..." to "Génération avec Glosc One...",
        "No images generated yet" to "Aucune image générée",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "Saisissez un prompt pour générer votre première image IA. Les résultats apparaîtront ici.",
        "Upload images" to "Importer des images",
        "Tap to select reference images" to "Touchez pour choisir des images de référence",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "JPG, PNG, GIF, WebP pris en charge · 16 images max.",
        "Clear" to "Effacer",
        "auto" to "auto",
        "Please upload an image and enter a prompt to generate" to "Importez une image et saisissez un prompt",
        "Please upload an image to generate" to "Importez une image pour générer",
        "Please enter a prompt to generate" to "Saisissez un prompt",
        "Ready to generate" to "Prêt à générer",
        "Please upload reference images first" to "Importez d'abord des images de référence",
        "Unable to read reference image" to "Impossible de lire l'image de référence",
        "Negative" to "Négatif",
        "Tags" to "Étiquettes",
        "Info" to "Infos",
        "Export" to "Exporter",
        "Delete" to "Supprimer",
        "Delete this image? This will remove the local file and database record." to "Supprimer cette image ? Le fichier local et l'enregistrement seront supprimés.",
        "Originals are preserved · Edits save as new images" to "Les originaux sont conservés · Les modifications sont enregistrées comme nouvelles images",
        "API Settings" to "Paramètres API",
        "Studio" to "Studio",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "Cette version conserve seulement Texte en image, Image en image et la connexion au modèle Glosc One.",
        "Fetch Models" to "Récupérer les modèles",
        "Save" to "Enregistrer",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "Les listes viennent de /v1/models. Seuls les modèles avec la catégorie image sont utilisés. Les clés API sont chiffrées avec Android Keystore.",
        "‹ Back" to "‹ Retour",
        "Share" to "Partager",
        "Use the system share sheet to send this image" to "Utilisez la feuille de partage système pour envoyer cette image",
        "Inpaint" to "Retouche",
        "Variation" to "Variation",
        "Upscale" to "Agrandir",
        "Outpaint" to "Étendre",
        "+ Add" to "+ Ajouter",
        "Add Tag" to "Ajouter une étiquette",
        "Source" to "Source",
        "Size" to "Taille",
        "Seed" to "Seed",
        "Random" to "Aléatoire",
        "Created" to "Créé",
        "Storage" to "Stockage",
        "Sample placeholder" to "Exemple",
        "Local file" to "Fichier local",
        "Processing..." to "Traitement...",
        "Fetch models first" to "Récupérez d'abord les modèles",
        "This sample has no local file to export" to "Cet exemple n'a aucun fichier local à exporter",
        "Export Image" to "Exporter l'image",
        "No image model fetched" to "Aucun modèle d'image récupéré",
        "here" to "ici",
        "Get your key from here" to "Obtenez votre clé ici",
        "Example: change the background to a bright day and keep the subject pose" to "Exemple : remplacez l'arrière-plan par un jour lumineux et gardez la pose",
        "Make the mood colder and add light snow" to "Rendez l'ambiance plus froide et ajoutez une neige légère",
        "Describe what to change and the target look. A new image will be generated." to "Décrivez quoi changer et l'apparence souhaitée. Une nouvelle image sera générée.",
        "Apply and Generate" to "Appliquer et générer",
        "Confirm" to "Confirmer",
        "Add" to "Ajouter"
    ),
    LanguageMode.Portuguese to mapOf(
        DEFAULT_PROMPT_EN to "Um beija-flor mecânico pairando ao lado de uma flor de vidro luminosa, fotografia macro, tons azuis frios, luz volumétrica, detalhes ultrafinos",
        "Follow system" to "Seguir sistema",
        "Language" to "Idioma",
        "Cancel" to "Cancelar",
        "Text to Image" to "Texto para imagem",
        "Image to Image" to "Imagem para imagem",
        "Chat" to "Chat",
        "Edit" to "Editar",
        "Transform" to "Transformar",
        "First Launch" to "Primeira abertura",
        "Setup Guide" to "Guia de configuração",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "Conclua a configuração para criar imagens: salve sua chave Glosc AI, busque modelos de imagem e escolha um modelo padrão.",
        "1. Connect Glosc AI" to "1. Conectar Glosc AI",
        "Channel" to "Canal",
        "2. Fetch Image Models" to "2. Buscar modelos de imagem",
        "The app calls /v1/models and keeps only models whose categories include image." to "O app chama /v1/models e mantém apenas modelos cuja categoria inclui image.",
        "No image models fetched yet" to "Nenhum modelo de imagem buscado",
        "Save Key and Fetch Models" to "Salvar chave e buscar modelos",
        "3. Choose Default Image Model" to "3. Escolher modelo padrão",
        "Default Model" to "Modelo padrão",
        "Text-to-image and image-to-image will use this model by default." to "Texto para imagem e imagem para imagem usarão este modelo por padrão.",
        "Start Creating" to "Começar a criar",
        "Glosc One image models ready" to "Modelos de imagem Glosc One prontos",
        "AI Studio" to "Estúdio AI",
        "IMAGE TOOLS" to "FERRAMENTAS",
        "Model" to "Modelo",
        "Source Images" to "Imagens fonte",
        "Prompt" to "Prompt",
        "Describe how you want to transform the images..." to "Descreva como deseja transformar as imagens...",
        "Describe the image you want to generate..." to "Descreva a imagem que deseja gerar...",
        "Image Size" to "Tamanho",
        "Resolution" to "Resolução",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "A proporção automática usa 1K. Resoluções maiores ficam desativadas até o modelo Glosc One selecionado indicar suporte.",
        "Generating..." to "Gerando...",
        "Generate" to "Gerar",
        "Failed to read reference image" to "Falha ao ler imagem de referência",
        "Generated Images" to "Imagens geradas",
        "Generating with Glosc One..." to "Gerando com Glosc One...",
        "No images generated yet" to "Nenhuma imagem gerada",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "Digite um prompt para gerar sua primeira imagem AI. Os resultados aparecerão aqui.",
        "Upload images" to "Enviar imagens",
        "Tap to select reference images" to "Toque para selecionar imagens de referência",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "Suporta JPG, PNG, GIF, WebP · Máx. 16 imagens",
        "Clear" to "Limpar",
        "auto" to "auto",
        "Please upload an image and enter a prompt to generate" to "Envie uma imagem e digite um prompt",
        "Please upload an image to generate" to "Envie uma imagem para gerar",
        "Please enter a prompt to generate" to "Digite um prompt",
        "Ready to generate" to "Pronto para gerar",
        "Please upload reference images first" to "Envie primeiro imagens de referência",
        "Unable to read reference image" to "Não foi possível ler a imagem de referência",
        "Negative" to "Negativo",
        "Tags" to "Tags",
        "Info" to "Info",
        "Export" to "Exportar",
        "Delete" to "Excluir",
        "Delete this image? This will remove the local file and database record." to "Excluir esta imagem? Isso removerá o arquivo local e o registro no banco.",
        "Originals are preserved · Edits save as new images" to "Originais são preservados · Edições salvam novas imagens",
        "API Settings" to "Configurações API",
        "Studio" to "Estúdio",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "Esta versão mantém apenas Texto para imagem, Imagem para imagem e a conexão Glosc One.",
        "Fetch Models" to "Buscar modelos",
        "Save" to "Salvar",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "As listas vêm de /v1/models. Apenas modelos com categoria image são usados. As chaves API são criptografadas pelo Android Keystore.",
        "‹ Back" to "‹ Voltar",
        "Share" to "Compartilhar",
        "Use the system share sheet to send this image" to "Use o compartilhamento do sistema para enviar esta imagem",
        "Inpaint" to "Retocar",
        "Variation" to "Variação",
        "Upscale" to "Ampliar",
        "Outpaint" to "Expandir",
        "+ Add" to "+ Adicionar",
        "Add Tag" to "Adicionar tag",
        "Source" to "Fonte",
        "Size" to "Tamanho",
        "Seed" to "Seed",
        "Random" to "Aleatório",
        "Created" to "Criado",
        "Storage" to "Armazenamento",
        "Sample placeholder" to "Exemplo",
        "Local file" to "Arquivo local",
        "Processing..." to "Processando...",
        "Fetch models first" to "Busque modelos primeiro",
        "This sample has no local file to export" to "Esta amostra não tem arquivo local para exportar",
        "Export Image" to "Exportar imagem",
        "No image model fetched" to "Nenhum modelo buscado",
        "here" to "aqui",
        "Get your key from here" to "Obtenha sua chave aqui",
        "Example: change the background to a bright day and keep the subject pose" to "Exemplo: troque o fundo por um dia claro e mantenha a pose",
        "Make the mood colder and add light snow" to "Deixe o clima mais frio e adicione neve leve",
        "Describe what to change and the target look. A new image will be generated." to "Descreva o que mudar e o visual desejado. Uma nova imagem será gerada.",
        "Apply and Generate" to "Aplicar e gerar",
        "Confirm" to "Confirmar",
        "Add" to "Adicionar"
    ),
    LanguageMode.Arabic to mapOf(
        DEFAULT_PROMPT_EN to "طائر طنان ميكانيكي يحوم بجانب زهرة زجاجية مضيئة، تصوير ماكرو، درجات زرقاء باردة، إضاءة حجمية، تفاصيل فائقة الدقة",
        "Follow system" to "اتباع النظام",
        "Language" to "اللغة",
        "Cancel" to "إلغاء",
        "Text to Image" to "نص إلى صورة",
        "Image to Image" to "صورة إلى صورة",
        "Chat" to "دردشة",
        "Edit" to "تحرير",
        "Transform" to "تحويل",
        "First Launch" to "التشغيل الأول",
        "Setup Guide" to "دليل الإعداد",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "أكمل الإعداد لبدء إنشاء الصور: احفظ مفتاح Glosc AI، واجلب نماذج الصور، واختر نموذجًا افتراضيًا.",
        "1. Connect Glosc AI" to "1. الاتصال بـ Glosc AI",
        "Channel" to "القناة",
        "2. Fetch Image Models" to "2. جلب نماذج الصور",
        "The app calls /v1/models and keeps only models whose categories include image." to "يستدعي التطبيق /v1/models ويحتفظ فقط بالنماذج التي تتضمن فئاتها image.",
        "No image models fetched yet" to "لم يتم جلب نماذج صور بعد",
        "Save Key and Fetch Models" to "حفظ المفتاح وجلب النماذج",
        "3. Choose Default Image Model" to "3. اختر نموذج الصورة الافتراضي",
        "Default Model" to "النموذج الافتراضي",
        "Text-to-image and image-to-image will use this model by default." to "سيستخدم نص إلى صورة وصورة إلى صورة هذا النموذج افتراضيًا.",
        "Start Creating" to "ابدأ الإنشاء",
        "Glosc One image models ready" to "نماذج صور Glosc One جاهزة",
        "AI Studio" to "استوديو AI",
        "IMAGE TOOLS" to "أدوات الصور",
        "Model" to "النموذج",
        "Source Images" to "صور المصدر",
        "Prompt" to "الوصف",
        "Describe how you want to transform the images..." to "صف كيف تريد تحويل الصور...",
        "Describe the image you want to generate..." to "صف الصورة التي تريد إنشاءها...",
        "Image Size" to "حجم الصورة",
        "Resolution" to "الدقة",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "النسبة التلقائية تستخدم 1K. تبقى الدقات الأعلى معطلة حتى يعلن نموذج Glosc One المحدد دعمها.",
        "Generating..." to "جارٍ الإنشاء...",
        "Generate" to "إنشاء",
        "Failed to read reference image" to "فشل قراءة الصورة المرجعية",
        "Generated Images" to "الصور المنشأة",
        "Generating with Glosc One..." to "جارٍ الإنشاء باستخدام Glosc One...",
        "No images generated yet" to "لم يتم إنشاء صور بعد",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "أدخل وصفًا لإنشاء أول صورة AI. ستظهر النتائج هنا.",
        "Upload images" to "رفع الصور",
        "Tap to select reference images" to "اضغط لاختيار صور مرجعية",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "يدعم JPG وPNG وGIF وWebP · حتى 16 صورة",
        "Clear" to "مسح",
        "auto" to "تلقائي",
        "Please upload an image and enter a prompt to generate" to "يرجى رفع صورة وإدخال وصف",
        "Please upload an image to generate" to "يرجى رفع صورة للإنشاء",
        "Please enter a prompt to generate" to "يرجى إدخال وصف",
        "Ready to generate" to "جاهز للإنشاء",
        "Please upload reference images first" to "يرجى رفع الصور المرجعية أولًا",
        "Unable to read reference image" to "تعذر قراءة الصورة المرجعية",
        "Negative" to "سلبي",
        "Tags" to "وسوم",
        "Info" to "معلومات",
        "Export" to "تصدير",
        "Delete" to "حذف",
        "Delete this image? This will remove the local file and database record." to "حذف هذه الصورة؟ سيؤدي ذلك إلى إزالة الملف المحلي وسجل قاعدة البيانات.",
        "Originals are preserved · Edits save as new images" to "يتم حفظ النسخ الأصلية · تحفظ التعديلات كصور جديدة",
        "API Settings" to "إعدادات API",
        "Studio" to "الاستوديو",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "يحتفظ هذا الإصدار فقط بنص إلى صورة وصورة إلى صورة واتصال نموذج Glosc One.",
        "Fetch Models" to "جلب النماذج",
        "Save" to "حفظ",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "تأتي قوائم النماذج من /v1/models. تُستخدم فقط النماذج التي تتضمن فئاتها image. يتم تشفير مفاتيح API باستخدام Android Keystore.",
        "‹ Back" to "‹ رجوع",
        "Share" to "مشاركة",
        "Use the system share sheet to send this image" to "استخدم لوحة المشاركة في النظام لإرسال هذه الصورة",
        "Inpaint" to "ترميم",
        "Variation" to "تنويع",
        "Upscale" to "رفع الدقة",
        "Outpaint" to "توسيع",
        "+ Add" to "+ إضافة",
        "Add Tag" to "إضافة وسم",
        "Source" to "المصدر",
        "Size" to "الحجم",
        "Seed" to "البذرة",
        "Random" to "عشوائي",
        "Created" to "تم الإنشاء",
        "Storage" to "التخزين",
        "Sample placeholder" to "مثال",
        "Local file" to "ملف محلي",
        "Processing..." to "جارٍ المعالجة...",
        "Fetch models first" to "اجلب النماذج أولًا",
        "This sample has no local file to export" to "لا يحتوي هذا المثال على ملف محلي للتصدير",
        "Export Image" to "تصدير الصورة",
        "No image model fetched" to "لم يتم جلب نموذج صورة",
        "here" to "هنا",
        "Get your key from here" to "احصل على مفتاحك من هنا",
        "Example: change the background to a bright day and keep the subject pose" to "مثال: غيّر الخلفية إلى يوم مشرق مع الحفاظ على وضعية العنصر",
        "Make the mood colder and add light snow" to "اجعل المزاج أبرد وأضف ثلجًا خفيفًا",
        "Describe what to change and the target look. A new image will be generated." to "صف ما تريد تغييره والمظهر المطلوب. سيتم إنشاء صورة جديدة.",
        "Apply and Generate" to "تطبيق وإنشاء",
        "Confirm" to "تأكيد",
        "Add" to "إضافة"
    ),
    LanguageMode.Russian to mapOf(
        DEFAULT_PROMPT_EN to "Механическая колибри парит рядом со светящимся стеклянным цветком, макросъемка, холодные синие тона, объемный свет, сверхтонкие детали",
        "Follow system" to "Как в системе",
        "Language" to "Язык",
        "Cancel" to "Отмена",
        "Text to Image" to "Текст в изображение",
        "Image to Image" to "Изображение в изображение",
        "Chat" to "Чат",
        "Edit" to "Редактировать",
        "Transform" to "Преобразовать",
        "First Launch" to "Первый запуск",
        "Setup Guide" to "Настройка",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "Завершите настройку: сохраните ключ Glosc AI, загрузите модели изображений и выберите модель по умолчанию.",
        "1. Connect Glosc AI" to "1. Подключить Glosc AI",
        "Channel" to "Канал",
        "2. Fetch Image Models" to "2. Загрузить модели",
        "The app calls /v1/models and keeps only models whose categories include image." to "Приложение вызывает /v1/models и оставляет только модели с категорией image.",
        "No image models fetched yet" to "Модели изображений еще не загружены",
        "Save Key and Fetch Models" to "Сохранить ключ и загрузить модели",
        "3. Choose Default Image Model" to "3. Выбрать модель по умолчанию",
        "Default Model" to "Модель по умолчанию",
        "Text-to-image and image-to-image will use this model by default." to "Текст в изображение и изображение в изображение будут использовать эту модель по умолчанию.",
        "Start Creating" to "Начать создание",
        "Glosc One image models ready" to "Модели Glosc One готовы",
        "AI Studio" to "AI-студия",
        "IMAGE TOOLS" to "ИНСТРУМЕНТЫ",
        "Model" to "Модель",
        "Source Images" to "Исходные изображения",
        "Prompt" to "Промпт",
        "Describe how you want to transform the images..." to "Опишите, как изменить изображения...",
        "Describe the image you want to generate..." to "Опишите изображение, которое хотите создать...",
        "Image Size" to "Размер",
        "Resolution" to "Разрешение",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "Автоформат использует 1K. Более высокие разрешения отключены, пока выбранная модель Glosc One не заявит поддержку.",
        "Generating..." to "Создание...",
        "Generate" to "Создать",
        "Failed to read reference image" to "Не удалось прочитать референс",
        "Generated Images" to "Созданные изображения",
        "Generating with Glosc One..." to "Создание через Glosc One...",
        "No images generated yet" to "Изображений пока нет",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "Введите промпт для первой AI-картинки. Результаты появятся здесь.",
        "Upload images" to "Загрузить изображения",
        "Tap to select reference images" to "Нажмите, чтобы выбрать референсы",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "JPG, PNG, GIF, WebP · до 16 изображений",
        "Clear" to "Очистить",
        "auto" to "авто",
        "Please upload an image and enter a prompt to generate" to "Загрузите изображение и введите промпт",
        "Please upload an image to generate" to "Загрузите изображение",
        "Please enter a prompt to generate" to "Введите промпт",
        "Ready to generate" to "Готово к созданию",
        "Please upload reference images first" to "Сначала загрузите референсы",
        "Unable to read reference image" to "Не удалось прочитать референс",
        "Negative" to "Негатив",
        "Tags" to "Теги",
        "Info" to "Инфо",
        "Export" to "Экспорт",
        "Delete" to "Удалить",
        "Delete this image? This will remove the local file and database record." to "Удалить изображение? Будут удалены локальный файл и запись базы данных.",
        "Originals are preserved · Edits save as new images" to "Оригиналы сохраняются · Правки сохраняются как новые изображения",
        "API Settings" to "Настройки API",
        "Studio" to "Студия",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "В этой версии оставлены только Текст в изображение, Изображение в изображение и подключение Glosc One.",
        "Fetch Models" to "Загрузить модели",
        "Save" to "Сохранить",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "Список моделей берется из /v1/models. Используются только модели с категорией image. API-ключи шифруются Android Keystore.",
        "‹ Back" to "‹ Назад",
        "Share" to "Поделиться",
        "Use the system share sheet to send this image" to "Используйте системное меню, чтобы отправить изображение",
        "Inpaint" to "Инпейнт",
        "Variation" to "Вариант",
        "Upscale" to "Апскейл",
        "Outpaint" to "Расширить",
        "+ Add" to "+ Добавить",
        "Add Tag" to "Добавить тег",
        "Source" to "Источник",
        "Size" to "Размер",
        "Seed" to "Seed",
        "Random" to "Случайно",
        "Created" to "Создано",
        "Storage" to "Хранилище",
        "Sample placeholder" to "Пример",
        "Local file" to "Локальный файл",
        "Processing..." to "Обработка...",
        "Fetch models first" to "Сначала загрузите модели",
        "This sample has no local file to export" to "У этого примера нет локального файла для экспорта",
        "Export Image" to "Экспорт изображения",
        "No image model fetched" to "Модель не загружена",
        "here" to "здесь",
        "Get your key from here" to "Получите ключ здесь",
        "Example: change the background to a bright day and keep the subject pose" to "Пример: заменить фон на яркий день и сохранить позу объекта",
        "Make the mood colder and add light snow" to "Сделать настроение холоднее и добавить легкий снег",
        "Describe what to change and the target look. A new image will be generated." to "Опишите, что изменить и какой вид нужен. Будет создано новое изображение.",
        "Apply and Generate" to "Применить и создать",
        "Confirm" to "Подтвердить",
        "Add" to "Добавить"
    ),
    LanguageMode.Bengali to mapOf(
        DEFAULT_PROMPT_EN to "একটি উজ্জ্বল কাচের ফুলের পাশে ভাসমান যান্ত্রিক হামিংবার্ড, ম্যাক্রো ফটোগ্রাফি, শীতল নীল টোন, ভলিউমেট্রিক আলো, অতি সূক্ষ্ম বিস্তারিত",
        "Follow system" to "সিস্টেম অনুসরণ করুন",
        "Language" to "ভাষা",
        "Cancel" to "বাতিল",
        "Text to Image" to "টেক্সট থেকে ছবি",
        "Image to Image" to "ছবি থেকে ছবি",
        "Chat" to "চ্যাট",
        "Edit" to "সম্পাদনা",
        "Transform" to "রূপান্তর",
        "First Launch" to "প্রথম চালু",
        "Setup Guide" to "সেটআপ গাইড",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "ছবি তৈরি শুরু করতে সেটআপ শেষ করুন: Glosc AI key সংরক্ষণ করুন, ইমেজ মডেল আনুন এবং ডিফল্ট মডেল বেছে নিন।",
        "1. Connect Glosc AI" to "1. Glosc AI সংযোগ করুন",
        "Channel" to "চ্যানেল",
        "2. Fetch Image Models" to "2. ইমেজ মডেল আনুন",
        "The app calls /v1/models and keeps only models whose categories include image." to "অ্যাপ /v1/models কল করে এবং শুধু image ক্যাটাগরি থাকা মডেল রাখে।",
        "No image models fetched yet" to "এখনও কোনো ইমেজ মডেল আনা হয়নি",
        "Save Key and Fetch Models" to "Key সংরক্ষণ করে মডেল আনুন",
        "3. Choose Default Image Model" to "3. ডিফল্ট ইমেজ মডেল বাছুন",
        "Default Model" to "ডিফল্ট মডেল",
        "Text-to-image and image-to-image will use this model by default." to "টেক্সট থেকে ছবি এবং ছবি থেকে ছবি ডিফল্টভাবে এই মডেল ব্যবহার করবে।",
        "Start Creating" to "তৈরি শুরু করুন",
        "Glosc One image models ready" to "Glosc One ইমেজ মডেল প্রস্তুত",
        "AI Studio" to "AI স্টুডিও",
        "IMAGE TOOLS" to "ইমেজ টুলস",
        "Model" to "মডেল",
        "Source Images" to "উৎস ছবি",
        "Prompt" to "প্রম্পট",
        "Describe how you want to transform the images..." to "ছবিগুলো কীভাবে বদলাতে চান লিখুন...",
        "Describe the image you want to generate..." to "আপনি যে ছবি তৈরি করতে চান তা লিখুন...",
        "Image Size" to "ছবির মাপ",
        "Resolution" to "রেজোলিউশন",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "স্বয়ংক্রিয় অনুপাত 1K ব্যবহার করে। নির্বাচিত Glosc One মডেল সমর্থন জানানো পর্যন্ত উচ্চ রেজোলিউশন বন্ধ থাকবে।",
        "Generating..." to "তৈরি হচ্ছে...",
        "Generate" to "তৈরি করুন",
        "Failed to read reference image" to "রেফারেন্স ছবি পড়া যায়নি",
        "Generated Images" to "তৈরি ছবি",
        "Generating with Glosc One..." to "Glosc One দিয়ে তৈরি হচ্ছে...",
        "No images generated yet" to "এখনও ছবি তৈরি হয়নি",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "প্রথম AI ছবি তৈরি করতে প্রম্পট লিখুন। ফলাফল এখানে দেখাবে।",
        "Upload images" to "ছবি আপলোড করুন",
        "Tap to select reference images" to "রেফারেন্স ছবি বাছতে ট্যাপ করুন",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "JPG, PNG, GIF, WebP সমর্থিত · সর্বোচ্চ 16 ছবি",
        "Clear" to "মুছুন",
        "auto" to "স্বয়ংক্রিয়",
        "Please upload an image and enter a prompt to generate" to "ছবি আপলোড করে প্রম্পট লিখুন",
        "Please upload an image to generate" to "তৈরি করতে একটি ছবি আপলোড করুন",
        "Please enter a prompt to generate" to "প্রম্পট লিখুন",
        "Ready to generate" to "তৈরির জন্য প্রস্তুত",
        "Please upload reference images first" to "প্রথমে রেফারেন্স ছবি আপলোড করুন",
        "Unable to read reference image" to "রেফারেন্স ছবি পড়া যায়নি",
        "Negative" to "নেগেটিভ",
        "Tags" to "ট্যাগ",
        "Info" to "তথ্য",
        "Export" to "রপ্তানি",
        "Delete" to "মুছুন",
        "Delete this image? This will remove the local file and database record." to "এই ছবি মুছবেন? স্থানীয় ফাইল ও ডাটাবেস রেকর্ড মুছে যাবে।",
        "Originals are preserved · Edits save as new images" to "মূল ছবি সংরক্ষিত থাকে · সম্পাদনা নতুন ছবি হিসেবে সেভ হয়",
        "API Settings" to "API সেটিংস",
        "Studio" to "স্টুডিও",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "এই সংস্করণে শুধু টেক্সট থেকে ছবি, ছবি থেকে ছবি এবং Glosc One মডেল সংযোগ রাখা হয়েছে।",
        "Fetch Models" to "মডেল আনুন",
        "Save" to "সেভ",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "মডেল তালিকা /v1/models থেকে আসে। শুধু image ক্যাটাগরি থাকা মডেল ব্যবহৃত হয়। API key Android Keystore দিয়ে এনক্রিপ্ট থাকে।",
        "‹ Back" to "‹ ফিরে",
        "Share" to "শেয়ার",
        "Use the system share sheet to send this image" to "এই ছবি পাঠাতে সিস্টেম শেয়ার শীট ব্যবহার করুন",
        "Inpaint" to "ইনপেইন্ট",
        "Variation" to "ভ্যারিয়েশন",
        "Upscale" to "আপস্কেল",
        "Outpaint" to "আউটপেইন্ট",
        "+ Add" to "+ যোগ",
        "Add Tag" to "ট্যাগ যোগ",
        "Source" to "উৎস",
        "Size" to "মাপ",
        "Seed" to "Seed",
        "Random" to "র্যান্ডম",
        "Created" to "তৈরি",
        "Storage" to "স্টোরেজ",
        "Sample placeholder" to "নমুনা",
        "Local file" to "স্থানীয় ফাইল",
        "Processing..." to "প্রক্রিয়াকরণ...",
        "Fetch models first" to "আগে মডেল আনুন",
        "This sample has no local file to export" to "এই নমুনার রপ্তানির জন্য স্থানীয় ফাইল নেই",
        "Export Image" to "ছবি রপ্তানি",
        "No image model fetched" to "কোনো ইমেজ মডেল আনা হয়নি",
        "here" to "এখানে",
        "Get your key from here" to "এখান থেকে key নিন",
        "Example: change the background to a bright day and keep the subject pose" to "উদাহরণ: ব্যাকগ্রাউন্ড উজ্জ্বল দিনে বদলান এবং ভঙ্গি রাখুন",
        "Make the mood colder and add light snow" to "মুড আরও ঠান্ডা করুন এবং হালকা তুষার যোগ করুন",
        "Describe what to change and the target look. A new image will be generated." to "কি বদলাতে চান এবং লক্ষ্য লুক লিখুন। নতুন ছবি তৈরি হবে।",
        "Apply and Generate" to "প্রয়োগ করে তৈরি",
        "Confirm" to "নিশ্চিত",
        "Add" to "যোগ"
    ),
    LanguageMode.Urdu to mapOf(
        DEFAULT_PROMPT_EN to "ایک میکانکی ہمنگ برڈ روشن شیشے کے پھول کے پاس منڈلا رہا ہے، میکرو فوٹوگرافی، ٹھنڈے نیلے رنگ، حجمی روشنی، انتہائی باریک تفصیل",
        "Follow system" to "سسٹم کے مطابق",
        "Language" to "زبان",
        "Cancel" to "منسوخ",
        "Text to Image" to "متن سے تصویر",
        "Image to Image" to "تصویر سے تصویر",
        "Chat" to "چیٹ",
        "Edit" to "ترمیم",
        "Transform" to "تبدیل",
        "First Launch" to "پہلا آغاز",
        "Setup Guide" to "سیٹ اپ گائیڈ",
        "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model." to "تصاویر بنانا شروع کرنے کے لیے سیٹ اپ مکمل کریں: اپنی Glosc AI key محفوظ کریں، تصویری ماڈلز لائیں، اور ڈیفالٹ ماڈل منتخب کریں۔",
        "1. Connect Glosc AI" to "1. Glosc AI سے جڑیں",
        "Channel" to "چینل",
        "2. Fetch Image Models" to "2. تصویری ماڈلز لائیں",
        "The app calls /v1/models and keeps only models whose categories include image." to "ایپ /v1/models کال کرتی ہے اور صرف image کیٹیگری والے ماڈلز رکھتی ہے۔",
        "No image models fetched yet" to "ابھی کوئی تصویری ماڈل نہیں آیا",
        "Save Key and Fetch Models" to "Key محفوظ کریں اور ماڈلز لائیں",
        "3. Choose Default Image Model" to "3. ڈیفالٹ تصویری ماڈل منتخب کریں",
        "Default Model" to "ڈیفالٹ ماڈل",
        "Text-to-image and image-to-image will use this model by default." to "متن سے تصویر اور تصویر سے تصویر ڈیفالٹ طور پر یہی ماڈل استعمال کریں گے۔",
        "Start Creating" to "بنانا شروع کریں",
        "Glosc One image models ready" to "Glosc One تصویری ماڈلز تیار ہیں",
        "AI Studio" to "AI اسٹوڈیو",
        "IMAGE TOOLS" to "تصویری ٹولز",
        "Model" to "ماڈل",
        "Source Images" to "ماخذ تصاویر",
        "Prompt" to "پرامپٹ",
        "Describe how you want to transform the images..." to "بتائیں آپ تصاویر کو کیسے بدلنا چاہتے ہیں...",
        "Describe the image you want to generate..." to "وہ تصویر بیان کریں جو آپ بنانا چاہتے ہیں...",
        "Image Size" to "تصویر کا سائز",
        "Resolution" to "ریزولوشن",
        "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support." to "خودکار نسبت 1K استعمال کرتی ہے۔ زیادہ ریزولوشن تب تک بند رہیں گے جب تک منتخب Glosc One ماڈل سپورٹ ظاہر نہ کرے۔",
        "Generating..." to "بن رہی ہے...",
        "Generate" to "بنائیں",
        "Failed to read reference image" to "حوالہ تصویر پڑھنے میں ناکامی",
        "Generated Images" to "بنائی گئی تصاویر",
        "Generating with Glosc One..." to "Glosc One سے بن رہی ہے...",
        "No images generated yet" to "ابھی کوئی تصویر نہیں بنی",
        "Enter a prompt to generate your first AI image. Your results will appear here." to "اپنی پہلی AI تصویر بنانے کے لیے پرامپٹ لکھیں۔ نتائج یہاں دکھیں گے۔",
        "Upload images" to "تصاویر اپلوڈ کریں",
        "Tap to select reference images" to "حوالہ تصاویر منتخب کرنے کے لیے ٹیپ کریں",
        "Supports JPG, PNG, GIF, WebP · Max 16 images" to "JPG، PNG، GIF، WebP سپورٹ · زیادہ سے زیادہ 16 تصاویر",
        "Clear" to "صاف",
        "auto" to "خودکار",
        "Please upload an image and enter a prompt to generate" to "براہ کرم تصویر اپلوڈ کریں اور پرامپٹ لکھیں",
        "Please upload an image to generate" to "براہ کرم تصویر اپلوڈ کریں",
        "Please enter a prompt to generate" to "براہ کرم پرامپٹ لکھیں",
        "Ready to generate" to "بنانے کے لیے تیار",
        "Please upload reference images first" to "براہ کرم پہلے حوالہ تصاویر اپلوڈ کریں",
        "Unable to read reference image" to "حوالہ تصویر پڑھی نہیں جا سکی",
        "Negative" to "منفی",
        "Tags" to "ٹیگز",
        "Info" to "معلومات",
        "Export" to "برآمد",
        "Delete" to "حذف",
        "Delete this image? This will remove the local file and database record." to "یہ تصویر حذف کریں؟ مقامی فائل اور ڈیٹابیس ریکارڈ ختم ہو جائیں گے۔",
        "Originals are preserved · Edits save as new images" to "اصل تصاویر محفوظ رہتی ہیں · ترامیم نئی تصاویر کے طور پر محفوظ ہوتی ہیں",
        "API Settings" to "API ترتیبات",
        "Studio" to "اسٹوڈیو",
        "This version keeps only Text to Image, Image to Image, and the Glosc One model connection." to "اس ورژن میں صرف متن سے تصویر، تصویر سے تصویر، اور Glosc One ماڈل کنکشن رکھا گیا ہے۔",
        "Fetch Models" to "ماڈلز لائیں",
        "Save" to "محفوظ",
        "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore." to "ماڈل فہرستیں /v1/models سے آتی ہیں۔ صرف image کیٹیگری والے ماڈلز استعمال ہوتے ہیں۔ API keys Android Keystore سے انکرپٹ ہوتی ہیں۔",
        "‹ Back" to "‹ واپس",
        "Share" to "شیئر",
        "Use the system share sheet to send this image" to "یہ تصویر بھیجنے کے لیے سسٹم شیئر شیٹ استعمال کریں",
        "Inpaint" to "ان پینٹ",
        "Variation" to "ورژن",
        "Upscale" to "اپ اسکیل",
        "Outpaint" to "آؤٹ پینٹ",
        "+ Add" to "+ شامل",
        "Add Tag" to "ٹیگ شامل کریں",
        "Source" to "ماخذ",
        "Size" to "سائز",
        "Seed" to "Seed",
        "Random" to "رینڈم",
        "Created" to "بنایا گیا",
        "Storage" to "اسٹوریج",
        "Sample placeholder" to "نمونہ",
        "Local file" to "مقامی فائل",
        "Processing..." to "عمل جاری ہے...",
        "Fetch models first" to "پہلے ماڈلز لائیں",
        "This sample has no local file to export" to "اس نمونے میں برآمد کے لیے مقامی فائل نہیں",
        "Export Image" to "تصویر برآمد کریں",
        "No image model fetched" to "کوئی تصویری ماڈل نہیں آیا",
        "here" to "یہاں",
        "Get your key from here" to "اپنی key یہاں سے لیں",
        "Example: change the background to a bright day and keep the subject pose" to "مثال: پس منظر کو روشن دن میں بدلیں اور موضوع کی پوز برقرار رکھیں",
        "Make the mood colder and add light snow" to "ماحول ٹھنڈا کریں اور ہلکی برف شامل کریں",
        "Describe what to change and the target look. A new image will be generated." to "بتائیں کیا بدلنا ہے اور مطلوبہ شکل کیا ہے۔ نئی تصویر بنے گی۔",
        "Apply and Generate" to "لاگو کر کے بنائیں",
        "Confirm" to "تصدیق",
        "Add" to "شامل"
    )
)

class MainActivity : ComponentActivity() {
    private lateinit var vm: MainViewModel
    private lateinit var root: LinearLayout

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        sourceImageUris.clear()
        sourceImageUris.addAll(uris.take(MAX_SOURCE_IMAGES))
        render()
    }

    private var studioMode = StudioMode.TextToImage
    private val sourceImageUris = mutableListOf<Uri>()
    private var languageMode = LanguageMode.Auto
    private var promptValue = DEFAULT_PROMPT_ZH
    private var negativeValue = "模糊, 低分辨率, 水印, 畸变"
    private var selectedSize = "auto"
    private var selectedQuality = "auto"
    private var selectedCount = 1
    private var seedValue = "284197"
    private var generateModel = ""
    private var libraryQuery = ""
    private var libraryFilter = "all"
    private var settingsProviderId = ""
    private var settingsName = ""
    private var settingsBaseUrl = ""
    private var settingsKey = ""
    private var settingsModel = ""
    private var settingsEnabled = true
    private var settingsProviderType = ProviderType.OpenAi
    private var libraryGridMode = true
    private var seedRefreshGenerationKey = ""
    private var shownUpdateTag = ""
    private var launchedUpdateApkPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languageMode = LanguageMode.fromKey(getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_LANGUAGE, LanguageMode.Auto.key))
        syncDefaultPromptForLanguage()
        window.statusBarColor = Design.Bg
        window.navigationBarColor = Design.Bg
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        root = column().apply {
            setBackgroundColor(Design.Bg)
        }
        setContentView(root)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.screen.collect { render() } }
                launch { vm.images.collect { render() } }
                launch { vm.recentTasks.collect { render() } }
                launch { vm.providers.collect { render() } }
                launch { vm.messages.collect { render() } }
                launch { vm.chatState.collect { render() } }
                launch { vm.operation.collect { render() } }
                launch { vm.settingsState.collect { render() } }
                launch { vm.updateState.collect { state -> handleUpdateState(state); render() } }
            }
        }
        render()
    }

    private fun render() {
        applyLanguageDirection()
        root.removeAllViews()
        when (vm.screen.value) {
            AppScreen.Onboarding -> renderOnboarding()
            AppScreen.Generate -> renderStudio()
            AppScreen.Chat -> renderStudio()
            AppScreen.Library -> renderStudio()
            AppScreen.Settings -> renderSettings()
            AppScreen.Detail -> renderDetail()
        }
    }

    private fun tr(en: String, zh: String): String {
        val language = activeLanguage()
        if (language == LanguageMode.Chinese) return zh
        if (language == LanguageMode.English) return en
        return LANGUAGE_TRANSLATIONS[language]?.get(en)
            ?: dynamicTranslation(language, en)
            ?: en
    }

    private fun activeLanguage(): LanguageMode =
        if (languageMode != LanguageMode.Auto) {
            languageMode
        } else {
            val language = resources.configuration.locales[0].language.lowercase(Locale.US)
            when (language) {
                "zh" -> LanguageMode.Chinese
                "hi" -> LanguageMode.Hindi
                "es" -> LanguageMode.Spanish
                "fr" -> LanguageMode.French
                "ar" -> LanguageMode.Arabic
                "bn" -> LanguageMode.Bengali
                "pt" -> LanguageMode.Portuguese
                "ru" -> LanguageMode.Russian
                "ur" -> LanguageMode.Urdu
                else -> LanguageMode.English
            }
        }

    private fun uiLocale(): Locale = activeLanguage().locale

    private fun applyLanguageDirection() {
        val direction = if (activeLanguage().rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        root.layoutDirection = direction
        window.decorView.layoutDirection = direction
    }

    private fun languageActionText(): String =
        if (languageMode == LanguageMode.Auto) "Auto ${activeLanguage().shortLabel}" else languageMode.shortLabel

    private fun showLanguageDialog() {
        val modes = LanguageMode.entries.toTypedArray()
        val labels = modes.map { mode ->
            if (mode == LanguageMode.Auto) {
                "${tr("Follow system", "跟随系统")} (${activeLanguage().nativeName})"
            } else {
                mode.nativeName
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(tr("Language", "语言"))
            .setSingleChoiceItems(labels, modes.indexOf(languageMode)) { dialog, which ->
                setLanguageMode(modes[which])
                dialog.dismiss()
            }
            .setNegativeButton(tr("Cancel", "取消"), null)
            .show()
    }

    private fun setLanguageMode(mode: LanguageMode) {
        languageMode = mode
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_LANGUAGE, mode.key)
            .apply()
        syncDefaultPromptForLanguage()
        render()
    }

    private fun syncDefaultPromptForLanguage() {
        if (isKnownDefaultPrompt(promptValue)) {
            promptValue = defaultPrompt()
        }
    }

    private fun defaultPrompt(): String = tr(DEFAULT_PROMPT_EN, DEFAULT_PROMPT_ZH)

    private fun isKnownDefaultPrompt(value: String): Boolean =
        value == DEFAULT_PROMPT_ZH ||
            value == DEFAULT_PROMPT_EN ||
            LANGUAGE_TRANSLATIONS.values.any { it[DEFAULT_PROMPT_EN] == value }

    private fun dynamicTranslation(language: LanguageMode, en: String): String? {
        Regex("""Found (\d+) image models""").matchEntire(en)?.let {
            val count = it.groupValues[1]
            return when (language) {
                LanguageMode.Hindi -> "$count इमेज मॉडल मिले"
                LanguageMode.Spanish -> "$count modelos de imagen encontrados"
                LanguageMode.French -> "$count modèles d'image trouvés"
                LanguageMode.Arabic -> "تم العثور على $count نماذج صور"
                LanguageMode.Bengali -> "${count}টি ইমেজ মডেল পাওয়া গেছে"
                LanguageMode.Portuguese -> "$count modelos de imagem encontrados"
                LanguageMode.Russian -> "Найдено моделей изображений: $count"
                LanguageMode.Urdu -> "$count تصویری ماڈلز ملے"
                else -> null
            }
        }
        Regex("""You have (\d+) creations""").matchEntire(en)?.let {
            val count = it.groupValues[1]
            return when (language) {
                LanguageMode.Hindi -> "आपके पास $count रचनाएं हैं"
                LanguageMode.Spanish -> "Tienes $count creaciones"
                LanguageMode.French -> "Vous avez $count créations"
                LanguageMode.Arabic -> "لديك $count إبداعات"
                LanguageMode.Bengali -> "আপনার ${count}টি সৃষ্টি আছে"
                LanguageMode.Portuguese -> "Você tem $count criações"
                LanguageMode.Russian -> "У вас $count работ"
                LanguageMode.Urdu -> "آپ کے پاس $count تخلیقات ہیں"
                else -> null
            }
        }
        Regex("""(\d+) selected""").matchEntire(en)?.let {
            val count = it.groupValues[1]
            return when (language) {
                LanguageMode.Hindi -> "$count चुनी गई"
                LanguageMode.Spanish -> "$count seleccionadas"
                LanguageMode.French -> "$count sélectionnées"
                LanguageMode.Arabic -> "تم اختيار $count"
                LanguageMode.Bengali -> "${count}টি নির্বাচিত"
                LanguageMode.Portuguese -> "$count selecionadas"
                LanguageMode.Russian -> "Выбрано: $count"
                LanguageMode.Urdu -> "$count منتخب"
                else -> null
            }
        }
        return null
    }

    private fun modeLabel(mode: StudioMode): String = when (mode) {
        StudioMode.TextToImage -> tr("Text to Image", "文生图")
        StudioMode.ImageToImage -> tr("Image to Image", "图生图")
    }

    private fun sourceTypeLabel(sourceType: SourceType): String = when (sourceType) {
        SourceType.Generate -> tr("Text to Image", "文生图")
        SourceType.ImageToImage -> tr("Image to Image", "图生图")
        SourceType.Chat -> tr("Chat", "对话")
        SourceType.Edit -> tr("Edit", "编辑")
        SourceType.Transform -> tr("Transform", "变换")
    }

    private fun renderOnboarding() {
        if (settingsProviderId.isBlank()) {
            (activeProvider() ?: vm.providers.value.firstOrNull())?.let { hydrateSettings(it, force = true) }
        }
        if (settingsName.isBlank()) settingsName = "Glosc AI"
        if (settingsBaseUrl.isBlank()) settingsBaseUrl = "https://one.gloscai.com/"
        settingsEnabled = true
        settingsProviderType = ProviderType.OpenAi

        root.addView(appBar(tr("First Launch", "首次启动"), tr("Setup Guide", "配置引导"), action = languageActionText()) { showLanguageDialog() })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        content.addSpaced(note(tr(
            "Finish setup to start creating images: save your Glosc AI key, fetch image models, and choose a default model.",
            "完成初始化后即可开始生成图片：保存 Glosc AI Key，获取图片模型列表，并选择默认模型。"
        )))

        content.addSpaced(section(tr("1. Connect Glosc AI", "1. 连接 Glosc AI")))
        content.addSpaced(card().apply {
            addSpaced(label(tr("Channel", "渠道")))
            addSpaced(input("https://one.gloscai.com/", settingsBaseUrl).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsBaseUrl = it?.toString().orEmpty() }
            })
            addSpaced(label("API Key"))
            addSpaced(input("sk-...", settingsKey).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsKey = it?.toString().orEmpty() }
            })
            addSpaced(keyLinkPrompt())
        })

        content.addSpaced(section(tr("2. Fetch Image Models", "2. 获取图片模型")))
        content.addSpaced(card().apply {
            addSpaced(bodyText(tr(
                "The app calls /v1/models and keeps only models whose categories include image.",
                "应用会请求 /v1/models，并只保留 categories 包含 image 的模型。"
            ), Design.Muted, 14f))
            val active = activeProvider()
            val count = active?.imageModels?.size ?: 0
            addSpaced(mono(
                if (count > 0) tr("Found $count image models", "已找到 $count 个图片模型") else tr("No image models fetched yet", "尚未获取图片模型"),
                if (count > 0) Design.Ok else Design.Faint,
                14f
            ))
            addSpaced(primaryButton(tr("Save Key and Fetch Models", "保存 Key 并获取模型列表")) {
                val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
                vm.saveProviderAndFetchModels(
                    id = settingsProviderId.ifBlank { "openai-default" },
                    name = settingsName,
                    baseUrl = settingsBaseUrl,
                    apiKey = settingsKey.takeIf { it.isNotBlank() },
                    type = settingsProviderType,
                    model = settingsModel.ifBlank { fallbackModel },
                    enabled = true
                )
            })
        })

        content.addSpaced(section(tr("3. Choose Default Image Model", "3. 选择默认图片模型")))
        content.addSpaced(card().apply {
            val modelOptions = imageModelOptions()
            addSpaced(dropdown(
                labelText = tr("Default Model", "默认模型"),
                options = modelOptions,
                selected = settingsModel.ifBlank { modelOptions.firstOrNull()?.first.orEmpty() }
            ) { settingsModel = it })
            addSpaced(bodyText(tr(
                "Text-to-image and image-to-image will use this model by default.",
                "文生图和图生图会默认使用这个模型。"
            ), Design.Muted, 14f))
        })

        renderSettingsState(content)
        content.addSpaced(primaryButton(tr("Start Creating", "完成初始化并开始使用")) {
            val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
            vm.saveProviderAndCompleteOnboarding(
                id = settingsProviderId.ifBlank { "openai-default" },
                name = settingsName,
                baseUrl = settingsBaseUrl,
                apiKey = settingsKey.takeIf { it.isNotBlank() },
                type = settingsProviderType,
                model = settingsModel.ifBlank { fallbackModel },
                enabled = true
            )
        })
        content.addGap(10)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderStudio() {
        refreshSeedAfterCompletedGenerate()
        val expanded = resources.configuration.screenWidthDp >= 700
        if (expanded) {
            root.addView(row(gap = 0).apply {
                addSpaced(studioSidebar(), LinearLayout.LayoutParams(dp(255), ViewGroup.LayoutParams.MATCH_PARENT))
                addSpaced(column(gap = 0).apply {
                    addSpaced(studioPromoBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
                    addSpaced(studioTopBar(expanded = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
                    addSpaced(studioWorkspace(expanded = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            root.addView(column(gap = 0).apply {
                addSpaced(studioPromoBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
                addSpaced(studioTopBar(expanded = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
                addSpaced(studioMobileTabs(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)))
                addSpaced(studioWorkspace(expanded = false), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun studioPromoBar(): View = row(padding = 0, gap = 8, gravity = Gravity.CENTER).apply {
        setBackgroundColor(Design.Accent2)
        addSpaced(bodyText(tr("Glosc One image models ready", "Glosc One 图像模型已就绪"), 0xFF100D05.toInt(), 15f).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun studioTopBar(expanded: Boolean): View = row(gap = 10).apply {
        setBackgroundColor(Design.Bg)
        setPadding(dp(16), 0, dp(16), 0)
        if (!expanded) {
            addSpaced(studioLogoCompact(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            addSpaced(row(gap = 22).apply {
                addSpaced(topNavText(modeLabel(StudioMode.TextToImage), studioMode == StudioMode.TextToImage) {
                    studioMode = StudioMode.TextToImage
                    render()
                })
                addSpaced(topNavText(modeLabel(StudioMode.ImageToImage), studioMode == StudioMode.ImageToImage) {
                    studioMode = StudioMode.ImageToImage
                    render()
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        addSpaced(ghostButton(languageActionText()) { showLanguageDialog() }, LinearLayout.LayoutParams(dp(92), dp(44)))
        addSpaced(ghostButton("API") {
            (activeProvider() ?: vm.providers.value.firstOrNull())?.let { hydrateSettings(it, force = true) }
            vm.open(AppScreen.Settings)
        }, LinearLayout.LayoutParams(dp(72), dp(44)))
    }

    private fun studioLogoCompact(): View = row(gap = 10).apply {
        addSpaced(brandLogo(sizeDp = 38, radiusDp = 10), LinearLayout.LayoutParams(dp(38), dp(38)))
        addSpaced(column(gap = 1).apply {
            addSpaced(title("Glosc Images", 20f))
            addSpaced(mono(tr("AI Studio", "AI 工作室"), Design.Faint, 12f))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun brandLogo(sizeDp: Int, radiusDp: Int): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.glosc_logo_source)
        scaleType = ImageView.ScaleType.CENTER_CROP
        roundedBg(Design.Surface2, radiusDp = radiusDp, strokeColor = Design.Border)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clipToOutline = true
        }
        contentDescription = "Glosc Images"
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun studioSidebar(): View = column(gap = 0).apply {
        setBackgroundColor(0xFF050506.toInt())
        addSpaced(row(gap = 8).apply {
            setPadding(dp(24), 0, dp(24), 0)
            addSpaced(bodyText(tr("AI Studio", "AI 工作室"), Design.Fg, 16f).apply {
                typeface = Typeface.DEFAULT_BOLD
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        addSpaced(row(gap = 10).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addSpaced(brandLogo(sizeDp = 42, radiusDp = 12), LinearLayout.LayoutParams(dp(42), dp(42)))
            addSpaced(title("Glosc Images", 22f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        addSpaced(column(padding = 8, gap = 6).apply {
            addSpaced(mono(tr("IMAGE TOOLS", "图像工具"), Design.Faint, 12f).apply {
                setPadding(dp(16), dp(18), 0, dp(4))
            })
            addSpaced(studioNavItem(modeLabel(StudioMode.TextToImage), studioMode == StudioMode.TextToImage) {
                studioMode = StudioMode.TextToImage
                render()
            })
            addSpaced(studioNavItem(modeLabel(StudioMode.ImageToImage), studioMode == StudioMode.ImageToImage) {
                studioMode = StudioMode.ImageToImage
                render()
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun studioMobileTabs(): View = row(gap = 8).apply {
        setBackgroundColor(Design.Bg)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addSpaced(studioNavItem(modeLabel(StudioMode.TextToImage), studioMode == StudioMode.TextToImage) {
            studioMode = StudioMode.TextToImage
            render()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addSpaced(studioNavItem(modeLabel(StudioMode.ImageToImage), studioMode == StudioMode.ImageToImage) {
            studioMode = StudioMode.ImageToImage
            render()
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
    }

    private fun studioWorkspace(expanded: Boolean): View {
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = column(padding = if (expanded) 24 else 16, gap = 16)
        if (expanded) {
            content.addSpaced(row(gap = 24, gravity = Gravity.TOP).apply {
                addSpaced(studioControlPanel(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
                addSpaced(studioResultsPanel(), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f))
            })
        } else {
            content.addSpaced(studioControlPanel())
            content.addSpaced(studioResultsPanel())
        }
        scroll.addView(content)
        return scroll
    }

    private fun studioControlPanel(): View = studioPanel().apply {
        addSpaced(label(tr("Model", "模型")))
        addSpaced(dropdown(
            labelText = "",
            options = generateModelOptions(),
            selected = activeImageModel()
        ) { generateModel = it })

        if (studioMode == StudioMode.ImageToImage) {
            addSpaced(label(tr("Source Images", "参考图片")))
            addSpaced(sourceUploadPanel())
        }

        addSpaced(label(tr("Prompt", "提示词")))
        val promptInput = input(
            if (studioMode == StudioMode.ImageToImage) {
                tr("Describe how you want to transform the images...", "描述你想如何改造这些图片...")
            } else {
                tr("Describe the image you want to generate...", "描述你想生成的图片...")
            },
            promptValue,
            minLines = 4
        ).apply {
            minHeight = dp(120)
            doAfterTextChanged { promptValue = it?.toString().orEmpty() }
        }
        addSpaced(promptInput)

        addSpaced(label(tr("Image Size", "图片尺寸")))
        addSpaced(dropdown(
            labelText = "",
            options = imageSizeOptions(),
            selected = selectedSize
        ) { selectedSize = it })

        addSpaced(row(gap = 10).apply {
            addSpaced(label(tr("Resolution", "分辨率")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpaced(resolutionButton("1K", enabled = true, selected = true))
            addSpaced(resolutionButton("2K", enabled = false, selected = false))
            addSpaced(resolutionButton("4K", enabled = false, selected = false))
        })
        addSpaced(bodyText(tr(
            "Auto ratio uses 1K. Higher resolutions stay disabled until the selected Glosc One model advertises support.",
            "自动比例使用 1K。更高分辨率会在所选 Glosc One 模型声明支持后启用。"
        ), Design.Faint, 13f))

        val generating = vm.operation.value is UiState.Loading
        addSpaced(bodyText(generationHint(promptInput.text?.toString().orEmpty()), Design.Muted, 14f).apply {
            gravity = Gravity.CENTER
        })
        addSpaced(primaryButton(if (generating) tr("Generating...", "生成中...") else tr("Generate", "生成")) {
            promptValue = promptInput.text?.toString().orEmpty()
            val sourcePaths = if (studioMode == StudioMode.ImageToImage) {
                runCatching { cacheSourceImages() }.getOrElse {
                    Toast.makeText(this@MainActivity, it.message ?: tr("Failed to read reference image", "参考图片读取失败"), Toast.LENGTH_LONG).show()
                    return@primaryButton
                }
            } else {
                emptyList()
            }
            vm.generate(
                GenerateImageRequest(
                    prompt = promptValue,
                    negativePrompt = "",
                    model = activeImageModel(),
                    size = selectedSize,
                    quality = selectedQuality,
                    count = selectedCount,
                    seed = seedValue,
                    sourceType = if (studioMode == StudioMode.ImageToImage) SourceType.ImageToImage else SourceType.Generate,
                    sourceImagePaths = sourcePaths
                )
            )
        }.apply { isEnabled = !generating })
    }

    private fun studioResultsPanel(): View = studioPanel(minHeightDp = 410).apply {
        val state = vm.operation.value
        val latestImages = when (state) {
            is UiState.Success -> state.data
            else -> vm.images.value
                .filter { it.localPath.isNotBlank() && File(it.localPath).exists() }
                .sortedByDescending { it.createdAt }
                .take(8)
        }
        addSpaced(row(gap = 8).apply {
            addSpaced(column(gap = 4).apply {
                val creationCount = vm.images.value.count { it.localPath.isNotBlank() && File(it.localPath).exists() }
                addSpaced(title(tr("Generated Images", "已生成图片"), 22f))
                addSpaced(bodyText(tr("You have $creationCount creations", "你有 $creationCount 张作品"), Design.Muted, 14f))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        when (state) {
            UiState.Loading -> {
                addGap(70)
                addSpaced(ProgressBar(this@MainActivity).apply {
                    isIndeterminate = true
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                })
                addSpaced(bodyText(tr("Generating with Glosc One...", "正在使用 Glosc One 生成..."), Design.Muted, 15f).apply {
                    gravity = Gravity.CENTER
                })
            }
            is UiState.Error -> {
                addGap(24)
                addSpaced(note(state.message, danger = true))
            }
            else -> {
                if (latestImages.isEmpty()) {
                    addGap(72)
                    addSpaced(bodyText("▧", Design.Faint, 42f).apply {
                        gravity = Gravity.CENTER
                    })
                    addSpaced(title(tr("No images generated yet", "还没有生成图片"), 20f).apply {
                        gravity = Gravity.CENTER
                    })
                    addSpaced(bodyText(tr(
                        "Enter a prompt to generate your first AI image. Your results will appear here.",
                        "输入提示词生成第一张 AI 图片，结果会显示在这里。"
                    ), Design.Muted, 14f).apply {
                        gravity = Gravity.CENTER
                    })
                } else {
                    addSpaced(imageGrid(latestImages))
                }
            }
        }
    }

    private fun studioPanel(minHeightDp: Int = 0): LinearLayout = column(padding = 24, gap = 14).apply {
        roundedBg(Design.Surface, radiusDp = 12, strokeColor = Design.Border)
        if (minHeightDp > 0) minimumHeight = dp(minHeightDp)
    }

    private fun studioNavItem(text: String, selected: Boolean, onClick: () -> Unit): View =
        FrameLayout(this).apply {
            roundedBg(if (selected) 0xFF1A1C1D.toInt() else 0x00000000, radiusDp = 6)
            val label = bodyText(text, if (selected) Design.Fg else Design.Muted, 16f).apply {
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(18), 0)
            }
            addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
            if (selected) {
                addView(View(this@MainActivity).apply {
                    roundedBg(Design.Accent, radiusDp = 2)
                }, FrameLayout.LayoutParams(dp(3), dp(22), Gravity.END or Gravity.CENTER_VERTICAL))
            }
            setOnClickListener { onClick() }
        }

    private fun topNavText(text: String, selected: Boolean, onClick: () -> Unit): View =
        bodyText(text, if (selected) Design.Fg else Design.Faint, 15f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
        }

    private fun sourceUploadPanel(): View = column(gap = 10).apply {
        addSpaced(FrameLayout(this@MainActivity).apply {
            dashedBg(Design.Surface2, radiusDp = 10, strokeColor = 0xFF3A3D40.toInt(), dashWidthDp = 5, dashGapDp = 4)
            addView(column(gap = 8, gravity = Gravity.CENTER).apply {
                addSpaced(bodyText("⇧", Design.Muted, 34f).apply {
                    gravity = Gravity.CENTER
                })
                addSpaced(title(tr("Upload images", "上传图片"), 20f).apply {
                    gravity = Gravity.CENTER
                })
                addSpaced(bodyText(tr("Tap to select reference images", "点击选择参考图片"), Design.Muted, 14f).apply {
                    gravity = Gravity.CENTER
                })
                addSpaced(bodyText(tr(
                    "Supports JPG, PNG, GIF, WebP · Max $MAX_SOURCE_IMAGES images",
                    "支持 JPG、PNG、GIF、WebP · 最多 $MAX_SOURCE_IMAGES 张"
                ), Design.Faint, 12f).apply {
                    gravity = Gravity.CENTER
                })
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
            setOnClickListener { imagePicker.launch("image/*") }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)))
        if (sourceImageUris.isNotEmpty()) {
            addSpaced(row(gap = 10).apply {
                addSpaced(bodyText(tr("${sourceImageUris.size} selected", "已选择 ${sourceImageUris.size} 张"), Design.Muted, 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(ghostButton(tr("Clear", "清空")) {
                    sourceImageUris.clear()
                    render()
                }, LinearLayout.LayoutParams(dp(78), dp(42)))
            })
            addSpaced(sourcePreviewStrip())
        }
    }

    private fun sourcePreviewStrip(): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val strip = row(gap = 8)
        sourceImageUris.forEach { uri ->
            strip.addSpaced(FrameLayout(this).apply {
                roundedBg(Design.Surface2, radiusDp = 8, strokeColor = Design.Border)
                addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(this).load(uri).centerCrop().into(this)
                }, FrameLayout.LayoutParams(dp(76), dp(76)))
            }, LinearLayout.LayoutParams(dp(76), dp(76)))
        }
        scroll.addView(strip)
        return scroll
    }

    private fun resolutionButton(text: String, enabled: Boolean, selected: Boolean): View =
        bodyText(text, if (selected) 0xFF171008.toInt() else Design.Faint, 14f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            roundedBg(
                color = when {
                    selected -> Design.Accent
                    enabled -> Design.Surface2
                    else -> 0xFF121314.toInt()
                },
                radiusDp = 7,
                strokeColor = if (selected) null else Design.Border
            )
            alpha = if (enabled || selected) 1f else 0.55f
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

    private fun imageSizeOptions(): List<Pair<String, String>> = listOf(
        "auto" to tr("auto", "自动"),
        "1024x1024" to "1:1",
        "1024x1536" to "2:3",
        "1536x1024" to "3:2"
    )

    private fun generationHint(prompt: String): String =
        when {
            studioMode == StudioMode.ImageToImage && sourceImageUris.isEmpty() && prompt.isBlank() ->
                tr("Please upload an image and enter a prompt to generate", "请上传图片并输入提示词")
            studioMode == StudioMode.ImageToImage && sourceImageUris.isEmpty() ->
                tr("Please upload an image to generate", "请先上传图片")
            prompt.isBlank() -> tr("Please enter a prompt to generate", "请输入提示词")
            else -> tr("Ready to generate", "可以开始生成")
        }

    private fun cacheSourceImages(): List<String> {
        if (sourceImageUris.isEmpty()) throw IllegalStateException(tr("Please upload reference images first", "请先上传参考图片"))
        val dir = File(cacheDir, "source-images").apply { mkdirs() }
        return sourceImageUris.take(MAX_SOURCE_IMAGES).mapIndexed { index, uri ->
            val ext = contentResolver.fileExtension(uri)
            val outFile = File(dir, "source_${System.currentTimeMillis()}_$index.$ext")
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(tr("Unable to read reference image", "无法读取参考图片"))
            input.use { source ->
                FileOutputStream(outFile).use { target -> source.copyTo(target) }
            }
            outFile.absolutePath
        }
    }

    private fun ContentResolver.fileExtension(uri: Uri): String {
        val fromMime = getType(uri)?.let { mime ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        }?.lowercase(Locale.US)
        if (!fromMime.isNullOrBlank()) return fromMime
        val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        return name?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.US)
            ?: "png"
    }

    private fun renderGenerate() {
        refreshSeedAfterCompletedGenerate()
        root.addView(appBar("工程模式", "生成图片", action = "历史任务") { showTasks() })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        content.addSpaced(generateConfigPanel())
        content.addSpaced(label("提示词"))
        val promptInput = input("描述你想要的画面", promptValue, minLines = 3).apply {
            minHeight = dp(86)
            doAfterTextChanged { promptValue = it?.toString().orEmpty() }
        }
        content.addSpaced(promptInput)
        val negativeInput = input("负向提示词（可选）", negativeValue).apply {
            minHeight = dp(48)
            doAfterTextChanged { negativeValue = it?.toString().orEmpty() }
        }
        content.addSpaced(negativeInput)

        val generating = vm.operation.value is UiState.Loading
        content.addSpaced(primaryButton(if (generating) "生成中..." else "生成图片") {
            promptValue = promptInput.text?.toString().orEmpty()
            negativeValue = negativeInput.text?.toString().orEmpty()
            vm.generate(
                GenerateImageRequest(
                    prompt = promptValue,
                    negativePrompt = negativeValue,
                    model = activeImageModel(),
                    size = selectedSize,
                    quality = selectedQuality,
                    count = selectedCount,
                    seed = seedValue,
                    sourceType = SourceType.Generate
                )
            )
        }.apply { isEnabled = !generating })

        renderGenerationState(content, showIdle = true)
        content.addSpaced(section("最近任务"))
        content.addSpaced(taskList(vm.recentTasks.value))
        content.addGap(8)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(AppScreen.Generate))
    }

    private fun refreshSeedAfterCompletedGenerate() {
        val state = vm.operation.value as? UiState.Success<List<ImageAsset>> ?: return
        val generated = state.data.filter { it.sourceType == SourceType.Generate }
        if (generated.isEmpty()) return
        val generationKey = generated.joinToString("|") { it.id }
        if (generationKey == seedRefreshGenerationKey) return
        seedRefreshGenerationKey = generationKey
        seedValue = nextSeedValue()
    }

    private fun nextSeedValue(): String =
        ThreadLocalRandom.current().nextInt(100000, 1_000_000).toString()

    private fun renderChat() {
        root.addView(appBar("对话模式", "创意助手", action = "新会话") { vm.newChat() })
        val wrap = column()
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val list = column(padding = 18, gap = 14)
        vm.messages.value.forEach { msg ->
            val image = msg.imageAssetId?.let { id -> vm.images.value.firstOrNull { it.id == id } }
            list.addSpaced(messageBubble(msg.role, msg.content, image))
        }
        if (vm.chatState.value is UiState.Loading) {
            list.addSpaced(messageBubble("assistant", "正在生成图片并保存到图库...", null))
        }
        scroll.addView(list)
        wrap.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        wrap.addView(composer())
        root.addView(wrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(AppScreen.Chat))
    }

    private fun renderLibrary() {
        root.addView(appBar("图片资产", "${filteredImages().size} 张作品", action = if (libraryGridMode) "列表视图" else "网格视图") {
            libraryGridMode = !libraryGridMode
            render()
        })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        content.addSpaced(row(gap = 8).apply {
            addSpaced(input("搜索提示词、标签、模型…", libraryQuery).apply {
                doAfterTextChanged { libraryQuery = it?.toString().orEmpty() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpaced(ghostButton("搜索") { render() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        })
        content.addSpaced(chipRow(
            options = listOf(
                "all" to "全部",
                "fav" to "★ 收藏",
                "Generate" to "工程",
                "Chat" to "对话",
                "Edit" to "编辑",
                "Transform" to "变换"
            ),
            selected = libraryFilter
        ) { libraryFilter = it; render() })

        val images = filteredImages()
        if (images.isEmpty()) {
            content.addGap(42)
            content.addSpaced(bodyText("还没有生成图片\n去生图页生成第一张作品", Design.Faint, 16f).apply {
                gravity = Gravity.CENTER
            })
        } else {
            content.addSpaced(if (libraryGridMode) imageGrid(images) else imageList(images))
        }
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNav(AppScreen.Library))
    }

    private fun renderDetail() {
        val asset = vm.selectedImageId.value?.let { id -> vm.images.value.firstOrNull { it.id == id } }
        if (asset == null) {
            vm.open(AppScreen.Library)
            return
        }
        root.addView(detailBar(asset))
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout
        content.addSpaced(detailHero(asset))
        content.addSpaced(operationGrid(asset))
        content.addSpaced(section(tr("Prompt", "提示词")))
        content.addSpaced(card().apply {
            addSpaced(bodyText(asset.prompt, size = 16f))
            asset.negativePrompt?.let { addSpaced(mono("${tr("Negative", "负向")}：$it", Design.Faint, 14f)) }
        })
        content.addSpaced(section(tr("Tags", "标签")))
        content.addSpaced(tagRow(asset))
        content.addSpaced(section(tr("Info", "信息")))
        content.addSpaced(assetMeta(asset))
        val row = row(gap = 10)
        row.addSpaced(ghostButton(tr("Export", "导出")) {
            shareImage(asset)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addSpaced(dangerButton(tr("Delete", "删除")) {
            confirm(tr(
                "Delete this image? This will remove the local file and database record.",
                "删除这张图片？将同时清理本地文件与数据库记录。"
            )) { vm.delete(asset) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addSpaced(row)
        content.addSpaced(mono(tr("Originals are preserved · Edits save as new images", "原图不会被覆盖 · 编辑结果保存为新图片"), Design.Faint, 14f).apply {
            gravity = Gravity.CENTER
        })
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderSettings() {
        if (settingsProviderId.isBlank()) {
            (activeProvider() ?: vm.providers.value.firstOrNull())?.let { hydrateSettings(it, force = true) }
        }
        root.addView(appBar("Glosc One", tr("API Settings", "API 设置"), action = tr("Studio", "返回 Studio")) { vm.open(AppScreen.Generate) })
        val body = scrollBody()
        val content = body.getChildAt(0) as LinearLayout

        settingsName = settingsName.ifBlank { "Glosc AI" }
        settingsBaseUrl = settingsBaseUrl.ifBlank { "https://one.gloscai.com/" }
        settingsEnabled = true
        settingsProviderType = ProviderType.OpenAi

        content.addSpaced(card().apply {
            addSpaced(row(gap = 10).apply {
                addSpaced(column(gap = 3).apply {
                    addSpaced(label(tr("Language", "语言")))
                    addSpaced(bodyText(
                        when (languageMode) {
                            LanguageMode.Auto -> tr("Follow system", "跟随系统")
                            else -> languageMode.nativeName
                        },
                        Design.Muted,
                        14f
                    ))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(ghostButton(languageActionText()) { showLanguageDialog() }, LinearLayout.LayoutParams(dp(100), dp(44)))
            })
        })
        content.addSpaced(note(tr(
            "This version keeps only Text to Image, Image to Image, and the Glosc One model connection.",
            "当前版本只保留 Text to Image、Image to Image 和 Glosc One 模型连接。"
        )))
        content.addSpaced(section("Glosc One"))
        content.addSpaced(card().apply {
            addSpaced(label("Base URL"))
            addSpaced(input("https://one.gloscai.com/", settingsBaseUrl).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsBaseUrl = it?.toString().orEmpty() }
            })
            addSpaced(label("API Key"))
            addSpaced(input("sk-...", settingsKey).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                typeface = android.graphics.Typeface.MONOSPACE
                doAfterTextChanged { settingsKey = it?.toString().orEmpty() }
            })
            addSpaced(keyLinkPrompt())
            val modelOptions = imageModelOptions()
            addSpaced(dropdown(
                labelText = tr("Default Model", "默认模型"),
                options = modelOptions,
                selected = settingsModel.ifBlank { modelOptions.firstOrNull()?.first.orEmpty() }
            ) { settingsModel = it })
        })

        val actions = row(gap = 10)
        actions.addSpaced(ghostButton(tr("Fetch Models", "获取模型列表")) {
            val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
            vm.saveProviderAndFetchModels(
                id = settingsProviderId.ifBlank { "openai-default" },
                name = settingsName,
                baseUrl = settingsBaseUrl,
                apiKey = settingsKey.takeIf { it.isNotBlank() },
                type = settingsProviderType,
                model = settingsModel.ifBlank { fallbackModel },
                enabled = true
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addSpaced(primaryButton(tr("Save", "保存")) {
            val fallbackModel = imageModelOptions().firstOrNull()?.first.orEmpty()
            vm.saveProvider(
                id = settingsProviderId.ifBlank { "openai-default" },
                name = settingsName,
                baseUrl = settingsBaseUrl,
                apiKey = settingsKey.takeIf { it.isNotBlank() },
                type = settingsProviderType,
                model = settingsModel.ifBlank { fallbackModel },
                enabled = settingsEnabled
            )
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addSpaced(actions)
        renderSettingsState(content)
        content.addSpaced(note(tr(
            "Model lists come from /v1/models. Only models whose categories include image are used. API keys are encrypted with Android Keystore.",
            "模型列表来自 /v1/models，仅使用 categories 包含 image 的模型作为图片模型。API Key 使用 Android Keystore 加密存储，不会写入明文或日志。"
        )))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun appBar(
        eyebrow: String,
        title: String,
        action: String? = null,
        onAction: (() -> Unit)? = null
    ): View = row(padding = 18, gap = 12).apply {
        val titleCol = column(gap = 2)
        titleCol.addSpaced(mono(eyebrow.uppercase(uiLocale()), Design.Accent, 14f))
        titleCol.addSpaced(title(title, 28f))
        addSpaced(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null && onAction != null) {
            addSpaced(ghostButton(action, onAction), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        }
    }

    private fun detailBar(asset: ImageAsset): View = row(padding = 18, gap = 10).apply {
        addSpaced(ghostButton(tr("‹ Back", "‹ 返回")) { vm.open(AppScreen.Library) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        addSpaced(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        addSpaced(ghostButton(if (asset.favorite) "★" else "☆") { vm.toggleFavorite(asset) }, LinearLayout.LayoutParams(dp(52), dp(52)))
        addSpaced(ghostButton(tr("Share", "分享")) {
            Toast.makeText(this@MainActivity, tr("Use the system share sheet to send this image", "可通过系统分享面板发送图片"), Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
    }

    private fun scrollBody(): ScrollView {
        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(column(padding = 18, gap = 14))
        return scroll
    }

    private fun section(text: String) = title(text, 20f).apply {
        setPadding(0, dp(8), 0, 0)
    }

    private fun generateConfigPanel(): View {
        val provider = activeProvider()
        return card(padding = 10).apply {
            val modelRow = row(gap = 8, gravity = Gravity.BOTTOM)
            modelRow.addSpaced(dropdown(
                labelText = "模型",
                options = generateModelOptions(),
                selected = activeImageModel()
            ) { generateModel = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            modelRow.addSpaced(ghostButton("设置") { vm.open(AppScreen.Settings) }, LinearLayout.LayoutParams(dp(72), dp(64)))
            addSpaced(modelRow)

            val params = row(gap = 8, gravity = Gravity.BOTTOM)
            params.addSpaced(dropdown(
                labelText = "尺寸",
                options = listOf("1024x1024" to "1024²", "1024x1536" to "1024×1536", "1536x1024" to "1536×1024"),
                selected = selectedSize
            ) { selectedSize = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
            params.addSpaced(dropdown(
                labelText = "质量",
                options = listOf("medium" to "标准", "high" to "高清", "auto" to "自动"),
                selected = selectedQuality
            ) { selectedQuality = it }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f))
            params.addSpaced(dropdown(
                labelText = "数量",
                options = listOf("1" to "1", "2" to "2", "4" to "4"),
                selected = selectedCount.toString()
            ) { selectedCount = it.toInt() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f))
            params.addSpaced(column(gap = 4).apply {
                addSpaced(label("种子").apply { textSize = 12f })
                addSpaced(input("随机", seedValue, numeric = true).apply {
                    textSize = 14f
                    minHeight = dp(46)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    typeface = android.graphics.Typeface.MONOSPACE
                    doAfterTextChanged { seedValue = it?.toString().orEmpty() }
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addSpaced(params)

            addSpaced(mono("${provider?.name ?: "Glosc AI"} · ${provider.displayModel()}", Design.Faint, 12f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    private fun renderGenerationState(content: LinearLayout, showIdle: Boolean = false) {
        when (val state = vm.operation.value) {
            UiState.Idle -> {
                if (showIdle) {
                    content.addSpaced(section("结果"))
                    content.addSpaced(card(padding = 12).apply {
                        addSpaced(bodyText("生成结果会显示在这里", Design.Faint, 15f).apply {
                            gravity = Gravity.CENTER
                        })
                    })
                }
            }
            UiState.Loading -> {
                content.addSpaced(section("结果"))
                content.addSpaced(card().apply {
                    addSpaced(mono("运行中", Design.Accent))
                    addSpaced(bodyText("正在请求图片模型并保存到本地文件。", Design.Muted))
                })
            }
            is UiState.Error -> {
                content.addSpaced(section("结果"))
                content.addSpaced(note(state.message, danger = true))
            }
            is UiState.Success -> {
                content.addSpaced(section("结果"))
                content.addSpaced(imageGrid(state.data))
            }
        }
    }

    private fun taskList(tasks: List<GenerationTask>): View = card().apply {
        if (tasks.isEmpty()) {
            addSpaced(bodyText("暂无任务记录", Design.Faint))
            return@apply
        }
        tasks.take(5).forEach { task ->
            val color = when (task.status) {
                TaskStatus.Success -> Design.Ok
                TaskStatus.Running -> Design.Accent
                TaskStatus.Failed -> Design.Danger
                TaskStatus.Pending -> Design.Warn
                TaskStatus.Cancelled -> Design.Faint
            }
            addSpaced(row(gap = 8).apply {
                addSpaced(mono("${task.taskType.label} · ${formatTime(task.createdAt)}", Design.Muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(mono(task.status.label, color))
            })
        }
    }

    private fun messageBubble(role: String, text: String, image: ImageAsset?): View {
        val outer = column(gap = 6).apply {
            gravity = if (role == "user") Gravity.END else Gravity.START
        }
        if (role != "user") outer.addSpaced(mono("助手", Design.Faint, 14f))
        val bubble = bodyText(text, if (role == "user") 0xFF102025.toInt() else Design.Fg, 16f).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            roundedBg(if (role == "user") Design.Accent else Design.Surface, radiusDp = 16, strokeColor = if (role == "user") null else Design.Border)
            maxWidth = resources.displayMetrics.widthPixels - dp(86)
        }
        outer.addSpaced(bubble, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        image?.let {
            outer.addSpaced(imageTile(it, 210), LinearLayout.LayoutParams(dp(210), dp(210)))
        }
        return outer
    }

    private fun composer(): View {
        val row = row(padding = 12, gap = 8).apply {
            roundedBg(0xEE20232A.toInt(), radiusDp = 0, strokeColor = Design.Border)
        }
        val sending = vm.chatState.value is UiState.Loading
        val input = input("继续描述或修改…", "", minLines = 1)
        input.isEnabled = !sending
        row.addSpaced(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addSpaced(primaryButton(if (sending) "生成中" else "发送") {
            val value = input.text?.toString().orEmpty()
            if (value.isNotBlank()) {
                input.setText("")
                vm.sendChat(value)
            }
        }.apply { isEnabled = !sending }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        return row
    }

    private fun imageGrid(images: List<ImageAsset>): GridLayout = GridLayout(this).apply {
        columnCount = 2
        images.forEachIndexed { index, asset ->
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(if (index % 3 == 0) 230 else 190)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(6))
            }
            addView(imageTile(asset, if (index % 3 == 0) 230 else 190), params)
        }
    }

    private fun imageTile(asset: ImageAsset, heightDp: Int): View = FrameLayout(this).apply {
        roundedBg(Design.Surface2, radiusDp = 12)
        val image = ImageView(this@MainActivity).apply { loadAsset(asset) }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)))
        val badge = mono(asset.prompt.take(18), Design.Fg, 13f).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            roundedBg(0xAA101218.toInt(), radiusDp = 6)
        }
        addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(8), dp(8), dp(8), dp(8))
        })
        if (asset.favorite) {
            addView(bodyText("★", Design.Warn, 18f), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                setMargins(dp(8), dp(6), dp(8), dp(8))
            })
        }
        setOnClickListener { vm.openDetail(asset.id) }
    }

    private fun imageList(images: List<ImageAsset>): View = column(gap = 10).apply {
        images.forEach { asset ->
            addSpaced(row(padding = 10, gap = 12).apply {
                roundedBg(Design.Surface, radiusDp = 12, strokeColor = Design.Border)
                addSpaced(FrameLayout(this@MainActivity).apply {
                    roundedBg(Design.Surface2, radiusDp = 10)
                    addView(ImageView(this@MainActivity).apply { loadAsset(asset) }, FrameLayout.LayoutParams(dp(76), dp(76)))
                }, LinearLayout.LayoutParams(dp(76), dp(76)))
                addSpaced(column(gap = 4).apply {
                    addSpaced(bodyText(asset.prompt, Design.Fg, 16f).apply {
                        maxLines = 2
                    })
                    addSpaced(mono("${sourceTypeLabel(asset.sourceType)} · ${asset.model}", Design.Faint, 14f))
                    addSpaced(mono(formatDate(asset.createdAt), Design.Faint, 14f))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addSpaced(bodyText(if (asset.favorite) "★" else "☆", if (asset.favorite) Design.Warn else Design.Faint, 18f))
                setOnClickListener { vm.openDetail(asset.id) }
            })
        }
    }

    private fun detailHero(asset: ImageAsset): View = FrameLayout(this).apply {
        roundedBg(Design.Surface2, radiusDp = 14)
        val image = ImageView(this@MainActivity).apply { loadAsset(asset) }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))
        val badge = mono("${asset.width}×${asset.height} · ${asset.model}", Design.Fg, 14f).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            roundedBg(0xAA101218.toInt(), radiusDp = 7)
        }
        addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(10), dp(10), dp(10), dp(10))
        })
    }

    private fun operationGrid(asset: ImageAsset): View = GridLayout(this).apply {
        columnCount = 4
        listOf(
            SourceType.Edit to tr("Inpaint", "局部重绘"),
            SourceType.Edit to tr("Variation", "变体"),
            SourceType.Transform to tr("Upscale", "超分"),
            SourceType.Edit to tr("Outpaint", "扩图")
        ).forEach { (type, label) ->
            val item = bodyText(label, Design.Fg, 15f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(14), dp(5), dp(14))
                roundedBg(Design.Surface, radiusDp = 10, strokeColor = Design.Border)
                setOnClickListener { showEditDialog(asset, type, label) }
            }
            addView(item, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun tagRow(asset: ImageAsset): View {
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = row(gap = 8)
        asset.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
            row.addSpaced(chip(it, selected = it == "动物" || it == "电影感"))
        }
        row.addSpaced(chip(tr("+ Add", "+ 添加")) { askText(tr("Add Tag", "添加标签")) { tag -> vm.addTag(asset, tag) } })
        scroller.addView(row)
        return scroller
    }

    private fun assetMeta(asset: ImageAsset): View = card().apply {
        val rows = listOf(
            tr("Source", "来源") to sourceTypeLabel(asset.sourceType),
            tr("Model", "模型") to asset.model,
            tr("Size", "尺寸") to "${asset.width} × ${asset.height}",
            tr("Seed", "种子") to (asset.seed ?: tr("Random", "随机")),
            tr("Created", "创建") to formatDate(asset.createdAt),
            tr("Storage", "存储") to if (asset.localPath.isBlank()) tr("Sample placeholder", "示例占位") else tr("Local file", "本地文件")
        )
        rows.forEach { (k, v) ->
            addSpaced(row(gap = 12).apply {
                addSpaced(mono(k, Design.Faint, 14f), LinearLayout.LayoutParams(dp(68), ViewGroup.LayoutParams.WRAP_CONTENT))
                addSpaced(bodyText(v, Design.Fg, 16f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
        }
    }

    private fun providerListItem(provider: ApiProvider): View = row(padding = 16, gap = 12).apply {
        roundedBg(if (provider.enabled) 0x222FD7E8 else Design.Surface, radiusDp = 14, strokeColor = if (provider.enabled) Design.Accent else Design.Border)
        val logo = bodyText(provider.name.take(2).uppercase(Locale.CHINA), if (provider.enabled) Design.Ok else Design.Accent2, 16f).apply {
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            roundedBg(if (provider.enabled) 0x2270E09A else 0x22D18CFF, radiusDp = 11)
        }
        addSpaced(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        addSpaced(column(gap = 3).apply {
            addSpaced(bodyText(provider.name, Design.Fg, 17f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD })
            addSpaced(mono("${provider.providerType.name.lowercase(Locale.CHINA)} · ${provider.baseUrl.removePrefix("https://")} · ${provider.displayModel()}", Design.Faint, 14f))
            provider.lastStatus?.let { addSpaced(mono(it, Design.Ok, 14f)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addSpaced(mono(if (provider.enabled) "已启用" else "未启用", if (provider.enabled) Design.Ok else Design.Faint, 14f))
        setOnClickListener {
            hydrateSettings(provider, force = true)
            render()
        }
    }

    private fun note(text: String, danger: Boolean = false): View = row(padding = 14, gap = 8).apply {
        roundedBg(if (danger) 0x22FF6F61 else 0x225ED7E8, radiusDp = 10, strokeColor = if (danger) Design.Danger else Design.Accent)
        addSpaced(
            bodyText(text, if (danger) Design.Danger else Design.Muted, 14f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun renderSettingsState(content: LinearLayout) {
        when (val state = vm.settingsState.value) {
            UiState.Idle -> Unit
            UiState.Loading -> content.addSpaced(note(tr("Processing...", "正在处理...")))
            is UiState.Success -> content.addSpaced(note(state.data))
            is UiState.Error -> content.addSpaced(note(state.message, danger = true))
        }
    }

    private fun updatePanel(): View = card().apply {
        addSpaced(mono("当前版本 ${currentVersionName()}", Design.Faint, 14f))
        when (val state = vm.updateState.value) {
            UiState.Idle -> {
                addSpaced(bodyText("从 GitHub Releases 检查最新 APK。", Design.Muted, 14f))
                addSpaced(primaryButton("检查更新") { vm.checkForUpdates() })
            }
            UiState.Loading -> {
                addSpaced(note("正在检查或下载更新..."))
            }
            is UiState.Error -> {
                addSpaced(note(state.message, danger = true))
                addSpaced(primaryButton("重新检查") { vm.checkForUpdates() })
            }
            is UiState.Success -> {
                val status = state.data
                val info = status.info
                addSpaced(note(status.message))
                if (info != null) {
                    addSpaced(mono("最新版本 ${info.tagName.ifBlank { info.latestVersionName }}", Design.Faint, 14f))
                    if (info.apkAssetName.isNotBlank()) {
                        addSpaced(mono("${info.apkAssetName} · ${formatBytes(info.apkSizeBytes)}", Design.Faint, 14f))
                    }
                    info.releaseNotes.takeIf { it.isNotBlank() }?.let {
                        addSpaced(bodyText(it.take(220), Design.Muted, 14f).apply {
                            maxLines = 4
                            ellipsize = TextUtils.TruncateAt.END
                        })
                    }
                }
                val actions = row(gap = 10)
                actions.addSpaced(ghostButton("重新检查") { vm.checkForUpdates() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                when {
                    !status.downloadedApkPath.isNullOrBlank() -> {
                        actions.addSpaced(primaryButton("安装更新") { installUpdateApk(status.downloadedApkPath) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    info?.updateAvailable == true -> {
                        actions.addSpaced(primaryButton("下载并安装") { vm.downloadUpdate(info) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    info?.htmlUrl?.isNotBlank() == true -> {
                        actions.addSpaced(ghostButton("发布页") { openUrl(info.htmlUrl) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                }
                addSpaced(actions)
            }
        }
    }

    private fun chipRow(
        options: List<Pair<String, String>>,
        selected: String,
        onSelect: (String) -> Unit
    ): View {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = row(gap = 8)
        options.forEach { (value, label) ->
            row.addSpaced(chip(label, selected = value == selected) { onSelect(value) })
        }
        scroll.addView(row)
        return scroll
    }

    private fun dropdown(
        labelText: String,
        options: List<Pair<String, String>>,
        selected: String,
        onSelect: (String) -> Unit
    ): View {
        val values = options.ifEmpty { listOf("" to "无可用选项") }
        val selectedIndex = values.indexOfFirst { it.first == selected }.takeIf { it >= 0 } ?: 0
        val labels = values.map { it.second }
        var lastValue = values[selectedIndex].first
        return column(gap = 4).apply {
            if (labelText.isNotBlank()) {
                addSpaced(label(labelText).apply { textSize = 12f })
            }
            addSpaced(Spinner(this@MainActivity).apply {
                adapter = object : ArrayAdapter<String>(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    labels
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        return styleSpinnerText(super.getView(position, convertView, parent), dropdown = false)
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        return styleSpinnerText(super.getDropDownView(position, convertView, parent), dropdown = true)
                    }
                }.apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                roundedBg(Design.Surface2, radiusDp = 9, strokeColor = Design.Border)
                minimumHeight = dp(46)
                setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Design.Surface2))
                setSelection(selectedIndex, false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val value = values[position].first
                        if (value != lastValue) {
                            lastValue = value
                            onSelect(value)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }
    }

    private fun styleSpinnerText(view: View, dropdown: Boolean): View {
        (view as? TextView)?.apply {
            setTextColor(Design.Fg)
            textSize = 14f
            typeface = if (dropdown) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(if (dropdown) 12 else 8), dp(10), dp(if (dropdown) 12 else 8))
            if (!dropdown) text = "${text}  ▾"
            if (dropdown) setBackgroundColor(Design.Surface2)
        }
        return view
    }

    private fun bottomNav(current: AppScreen): View = row(padding = 10, gap = 4).apply {
        roundedBg(0xEE20232A.toInt(), radiusDp = 0, strokeColor = Design.Border)
        listOf(
            AppScreen.Generate to "生图",
            AppScreen.Chat to "对话",
            AppScreen.Library to "图片库",
            AppScreen.Settings to "设置"
        ).forEach { (screen, label) ->
            val item = bodyText(label, if (screen == current) Design.Accent else Design.Faint, 14f).apply {
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { vm.open(screen) }
            }
            addSpaced(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun activeProvider(): ApiProvider? = vm.providers.value.firstOrNull { it.enabled } ?: vm.providers.value.firstOrNull()

    private fun activeImageModel(): String {
        val provider = activeProvider()
        val models = provider?.imageModels.orEmpty()
        return generateModel.takeIf { selected -> selected.isNotBlank() && (models.isEmpty() || selected in models) }
            ?: provider?.defaultModel
            ?.ifBlank { provider.imageModels.firstOrNull().orEmpty() }
            .orEmpty()
    }

    private fun hydrateSettings(provider: ApiProvider, force: Boolean = false) {
        if (!force && settingsProviderId == provider.id) return
        settingsProviderId = provider.id
        settingsName = provider.name
        settingsBaseUrl = provider.baseUrl
        settingsKey = ""
        settingsModel = provider.defaultModel.ifBlank { provider.imageModels.firstOrNull().orEmpty() }
        settingsEnabled = provider.enabled
        settingsProviderType = provider.providerType
    }

    private fun imageModelOptions(): List<Pair<String, String>> {
        val provider = vm.providers.value.firstOrNull { it.id == settingsProviderId }
            ?: if (settingsProviderId.isBlank()) activeProvider() else null
        val models = provider?.imageModels.orEmpty()
        val selected = settingsModel.ifBlank { provider?.defaultModel.orEmpty() }
        val options = (models + selected)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (options.isEmpty()) {
            listOf("" to tr("Fetch models first", "先获取模型列表"))
        } else {
            options.map { it to it }
        }
    }

    private fun generateModelOptions(): List<Pair<String, String>> {
        val provider = activeProvider()
        val options = (provider?.imageModels.orEmpty() + provider?.defaultModel.orEmpty() + generateModel)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return if (options.isEmpty()) {
            listOf("" to tr("Fetch models first", "先获取模型"))
        } else {
            options.map { it to it }
        }
    }

    private fun handleUpdateState(state: UiState<AppUpdateStatus>) {
        val status = (state as? UiState.Success)?.data ?: return
        status.downloadedApkPath?.takeIf { it.isNotBlank() }?.let { path ->
            if (path != launchedUpdateApkPath) {
                launchedUpdateApkPath = path
                installUpdateApk(path)
            }
            return
        }
        val info = status.info ?: return
        if (info.updateAvailable && info.tagName != shownUpdateTag) {
            shownUpdateTag = info.tagName
            showUpdateDialog(info)
        }
    }

    private fun showUpdateDialog(info: AppUpdateInfo) {
        val notes = info.releaseNotes.take(300).ifBlank { "此版本没有填写发布说明。" }
        AlertDialog.Builder(this)
            .setTitle("发现新版本 ${info.tagName}")
            .setMessage(
                "当前版本：${info.currentVersionName}\n" +
                    "安装包：${info.apkAssetName} · ${formatBytes(info.apkSizeBytes)}\n\n" +
                    notes
            )
            .setPositiveButton("下载并安装") { _, _ -> vm.downloadUpdate(info) }
            .setNeutralButton("发布页") { _, _ -> openUrl(info.htmlUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun installUpdateApk(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "更新包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle("允许安装更新")
                .setMessage("需要先允许 GloscAI Images 安装 APK。授权后回到设置页，点击“安装更新”。")
                .setPositiveButton("去授权") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "无法打开系统安装器：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "无法打开链接：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun shareImage(asset: ImageAsset) {
        val file = asset.localPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (file == null || !file.exists()) {
            Toast.makeText(this, tr("This sample has no local file to export", "示例图暂无可导出的本地文件"), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, tr("Export Image", "导出图片")))
    }

    private fun ApiProvider?.displayModel(): String {
        if (this == null) return tr("No image model fetched", "未获取图片模型")
        return defaultModel.ifBlank { imageModels.firstOrNull().orEmpty() }.ifBlank { tr("No image model fetched", "未获取图片模型") }
    }

    private fun keyLinkPrompt(): View {
        val linkText = tr("here", "这里")
        val text = tr("Get your key from here", "从 这里 获取 key")
        val span = SpannableString(text)
        val start = text.indexOf(linkText)
        span.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://one.gloscai.com/keys")))
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Design.Accent
                    ds.isUnderlineText = true
                    ds.typeface = Typeface.DEFAULT_BOLD
                }
            },
            start,
            start + linkText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return bodyText("", Design.Muted, 14f).apply {
            setText(span)
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = android.graphics.Color.TRANSPARENT
        }
    }

    private fun filteredImages(): List<ImageAsset> {
        val q = libraryQuery.trim().lowercase(Locale.CHINA)
        return vm.images.value.filter { asset ->
            asset.localPath.isNotBlank() && File(asset.localPath).exists()
        }.filter { asset ->
            val okFilter = when (libraryFilter) {
                "all" -> true
                "fav" -> asset.favorite
                else -> asset.sourceType.name == libraryFilter
            }
            val haystack = "${asset.prompt} ${asset.tags} ${asset.model}".lowercase(Locale.CHINA)
            okFilter && (q.isBlank() || haystack.contains(q))
        }
    }

    private fun showEditDialog(asset: ImageAsset, type: SourceType, label: String) {
        val input = input(
            tr("Example: change the background to a bright day and keep the subject pose", "例如：把背景换成晴朗白天，保留主体姿态"),
            tr("Make the mood colder and add light snow", "把氛围调得更冷，加入微雪"),
            minLines = 3
        )
        AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage(tr("Describe what to change and the target look. A new image will be generated.", "描述要修改的区域与目标效果，将生成一张新图片。"))
            .setView(input)
            .setPositiveButton(tr("Apply and Generate", "应用并生成")) { _, _ -> vm.edit(asset, input.text?.toString().orEmpty(), type) }
            .setNegativeButton(tr("Cancel", "取消"), null)
            .show()
    }

    private fun showTasks() {
        val text = vm.recentTasks.value.take(8).joinToString("\n") {
            "${it.taskType.label} · ${it.status.label} · ${formatTime(it.createdAt)}"
        }.ifBlank { "暂无任务" }
        AlertDialog.Builder(this)
            .setTitle("任务历史")
            .setMessage(text)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun confirm(message: String, onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(tr("Confirm", "确认")) { _, _ -> onOk() }
            .setNegativeButton(tr("Cancel", "取消"), null)
            .show()
    }

    private fun askText(title: String, onOk: (String) -> Unit) {
        val input = input(title)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(tr("Add", "添加")) { _, _ ->
                input.text?.toString()?.takeIf { it.isNotBlank() }?.let(onOk)
            }
            .setNegativeButton(tr("Cancel", "取消"), null)
            .show()
    }

    private fun formatTime(time: Long): String = SimpleDateFormat("HH:mm", uiLocale()).format(Date(time))

    private fun formatDate(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", uiLocale()).format(Date(time))

    private fun currentVersionName(): String {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return info.versionName ?: "0.0.0"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "未知大小"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit += 1
        }
        return if (unit == 0) "${bytes}B" else String.format(Locale.US, "%.1f%s", value, units[unit])
    }
}
