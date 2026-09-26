import type { components } from "./schema";

type Schemas = components["schemas"];

export type SeriesCard = Schemas["SeriesCard"];
export type SeriesDetail = Schemas["SeriesDetail"];
export type ChapterSummary = Schemas["ChapterSummary"];
export type ChapterContent = Schemas["ChapterContent"];
export type Genre = Schemas["Genre"];
export type ImageRef = Schemas["ImageRef"];
export type HomeResponse = Schemas["HomeResponse"];
export type RankedSeries = Schemas["RankedSeries"];
export type SeriesPage = Schemas["CursorPageSeriesCard"];
export type ChapterPage = Schemas["CursorPageChapterSummary"];
export type SeriesType = SeriesCard["type"];
export type SeriesStatus = SeriesCard["status"];
