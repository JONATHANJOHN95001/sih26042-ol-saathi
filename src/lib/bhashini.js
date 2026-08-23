/**
 * Bhashini: translate, speak, listen.
 *
 * Two calls, always. First ask the pipeline which model can do the job
 * and where to send it, then send it. The config response also hands
 * back the auth header name and value for the compute call, which is
 * easy to miss: it is NOT the same key you used for config.
 *
 * Everything is cached in IndexedDB on the way through. That is not a
 * demo trick, it is what any app shipping to schools with no signal
 * would do, and it is also the fallback when the network dies on stage.
 */

import { cacheGet, cachePut } from './cache.js'

const CONFIG_URL =
  'https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline'
const PIPELINE_ID = '64392f96daac500b55c543cd' // MeitY

const USER_ID = import.meta.env.VITE_BHASHINI_USER_ID
const ULCA_KEY = import.meta.env.VITE_BHASHINI_ULCA_KEY

/** Pipeline configs rarely change, so resolve each task shape once. */
const pipelineCache = new Map()

async function getPipeline(tasks) {
  const key = JSON.stringify(tasks)
  if (pipelineCache.has(key)) return pipelineCache.get(key)

  const res = await fetch(CONFIG_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      userID: USER_ID,
      ulcaApiKey: ULCA_KEY,
    },
    body: JSON.stringify({
      pipelineTasks: tasks,
      pipelineRequestConfig: { pipelineId: PIPELINE_ID },
    }),
  })
  if (!res.ok) throw new Error(`bhashini config ${res.status}`)

  const cfg = await res.json()
  const endpoint = cfg.pipelineInferenceAPIEndPoint
  const resolved = {
    url: endpoint?.callbackUrl,
    authName: endpoint?.inferenceApiKey?.name,
    authValue: endpoint?.inferenceApiKey?.value,
    services: cfg.pipelineResponseConfig?.map((t) => t.config?.[0]?.serviceId),
  }
  if (!resolved.url || !resolved.services?.[0]) {
    throw new Error('bhashini: no model offered for this language pair')
  }
  pipelineCache.set(key, resolved)
  return resolved
}

async function compute(p, tasks, inputData) {
  const res = await fetch(p.url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', [p.authName]: p.authValue },
    body: JSON.stringify({ pipelineTasks: tasks, inputData }),
  })
  if (!res.ok) throw new Error(`bhashini compute ${res.status}`)
  return res.json()
}

/* ---------------------------------------------------------------- */

/** Hindi in, mother tongue out. */
export async function translate(text, { from = 'hi', to = 'sat' } = {}) {
  const key = `t:${from}:${to}:${text}`
  const hit = await cacheGet(key)
  if (hit) return hit

  const shape = [
    { taskType: 'translation', config: { language: { sourceLanguage: from, targetLanguage: to } } },
  ]
  const p = await getPipeline(shape)
  const out = await compute(
    p,
    [{ taskType: 'translation', config: { language: { sourceLanguage: from, targetLanguage: to }, serviceId: p.services[0] } }],
    { input: [{ source: text }] },
  )

  const target = out.pipelineResponse?.[0]?.output?.[0]?.target
  if (!target) throw new Error('bhashini: empty translation')
  await cachePut(key, target)
  return target
}

/**
 * Text to speech. Returns a base64 wav payload, which is what the
 * pipeline hands back rather than a URL.
 */
export async function speak(text, { lang = 'sat', gender = 'female' } = {}) {
  const key = `s:${lang}:${gender}:${text}`
  const hit = await cacheGet(key)
  if (hit) return hit

  const shape = [{ taskType: 'tts', config: { language: { sourceLanguage: lang } } }]
  const p = await getPipeline(shape)
  const out = await compute(
    p,
    [{ taskType: 'tts', config: { language: { sourceLanguage: lang }, serviceId: p.services[0], gender } }],
    { input: [{ source: text }] },
  )

  const b64 = out.pipelineResponse?.[0]?.audio?.[0]?.audioContent
  if (!b64) throw new Error('bhashini: empty audio')
  const uri = `data:audio/wav;base64,${b64}`
  await cachePut(key, uri)
  return uri
}

/**
 * Speech to text. Every Hindi and Santali model on the platform is
 * BATCH, not streaming, so this takes a finished recording. There is no
 * word-by-word live caption to be had; the interface has to be press to
 * talk, and the pitch should say so rather than imply otherwise.
 */
export async function listen(base64Wav, { lang = 'hi' } = {}) {
  const shape = [{ taskType: 'asr', config: { language: { sourceLanguage: lang } } }]
  const p = await getPipeline(shape)
  const out = await compute(
    p,
    [{ taskType: 'asr', config: { language: { sourceLanguage: lang }, serviceId: p.services[0] } }],
    { audio: [{ audioContent: base64Wav }] },
  )
  return out.pipelineResponse?.[0]?.output?.[0]?.source ?? ''
}

/** Hindi line to Santali text plus audio, the core move of the app. */
export async function deliver(hindi, { to = 'sat' } = {}) {
  const text = await translate(hindi, { to })
  let audio = null
  try {
    audio = await speak(text, { lang: to })
  } catch {
    // Ho and several others translate but have no voice. Text only is a
    // valid result, not an error, and the UI says so.
    audio = null
  }
  return { text, audio, spoken: Boolean(audio) }
}

/**
 * Every language the platform actually serves, checked against the live
 * catalogue rather than assumed.
 *
 * 21 of the 22 scheduled languages have the complete pipeline: translate,
 * speak and listen. Konkani has none of it. Ho translates but cannot
 * speak, and Mundari, Kurukh, Kharia, Sadri and Kurmali are absent
 * entirely, because Bhashini covers the Eighth Schedule and those five
 * Jharkhand tribal languages are not on it. That is a policy gap, not a
 * bug, and the pitch should say so.
 *
 * `speaks` and `listens` drive the interface, so a language that can only
 * do text says so on its own chip instead of the app quietly doing less.
 */
const FULL = (code, name, nameEn) => ({ code, name, nameEn, speaks: true, listens: true })

export const LANGUAGES = [
  // Jharkhand first. This is who asked.
  FULL('sat', 'ᱥᱟᱱᱛᱟᱲᱤ', 'Santali'),
  { code: 'hoc', name: 'Ho', nameEn: 'Ho', speaks: false, listens: false },
  FULL('bn', 'বাংলা', 'Bengali'),
  FULL('or', 'ଓଡ଼ିଆ', 'Odia'),
  FULL('ur', 'اردو', 'Urdu'),
  FULL('mai', 'मैथिली', 'Maithili'),
  // and the rest of the Eighth Schedule, which came free
  FULL('as', 'অসমীয়া', 'Assamese'),
  FULL('brx', 'बड़ो', 'Bodo'),
  FULL('doi', 'डोगरी', 'Dogri'),
  FULL('gu', 'ગુજરાતી', 'Gujarati'),
  FULL('kn', 'ಕನ್ನಡ', 'Kannada'),
  FULL('ks', 'کٲشُر', 'Kashmiri'),
  FULL('ml', 'മലയാളം', 'Malayalam'),
  FULL('mni', 'ꯃꯤꯇꯩ', 'Manipuri'),
  FULL('mr', 'मराठी', 'Marathi'),
  FULL('ne', 'नेपाली', 'Nepali'),
  FULL('pa', 'ਪੰਜਾਬੀ', 'Punjabi'),
  FULL('sa', 'संस्कृतम्', 'Sanskrit'),
  FULL('sd', 'سنڌي', 'Sindhi'),
  FULL('ta', 'தமிழ்', 'Tamil'),
  FULL('te', 'తెలుగు', 'Telugu'),
  FULL('en', 'English', 'English'),
]

/** The six we show as chips. The rest live behind the picker. */
export const PRIMARY = ['sat', 'hoc', 'bn', 'or', 'ur', 'mai']
