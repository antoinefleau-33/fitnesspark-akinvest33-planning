package fr.freeplay.tv

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Les tuiles de l'accueil. Au focus (manette ou doigt), la tuile grandit et
 * se surligne — le repère visuel d'Android TV.
 */
class TileAdapter(
    private val context: Context,
    private val onClick: (Tile) -> Unit
) : RecyclerView.Adapter<TileAdapter.TileHolder>() {

    private var items: List<Tile> = emptyList()

    fun submit(tiles: List<Tile>) {
        items = tiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tile, parent, false)
        return TileHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TileHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: View = view.findViewById(R.id.card)
        private val title: TextView = view.findViewById(R.id.title)
        private val subtitle: TextView = view.findViewById(R.id.subtitle)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val badge: TextView = view.findViewById(R.id.badge)

        fun bind(tile: Tile) {
            title.text = tile.title
            subtitle.text = tile.subtitle

            // Fond dégradé aux couleurs de l'application.
            val background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tile.colorStart, tile.colorEnd)
            ).apply {
                cornerRadius = context.resources
                    .getDimension(R.dimen.tile_corner)
            }
            card.background = background

            // Icône réelle de l'app si elle est installée, sinon icône interne.
            val installed = Tiles.isInstalled(context, tile.packageName)
            val appIcon = tile.packageName
                ?.takeIf { installed }
                ?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }

            if (appIcon != null) {
                icon.setImageDrawable(appIcon)
            } else {
                icon.setImageResource(tile.fallbackIcon)
            }

            // Une app absente reste visible et propose son installation.
            badge.visibility = if (installed) View.GONE else View.VISIBLE

            itemView.setOnClickListener { onClick(tile) }
            itemView.setOnFocusChangeListener { v, hasFocus -> animateFocus(v, hasFocus) }
            // État de départ, sinon les tuiles recyclées gardent l'échelle du focus.
            animateFocus(itemView, itemView.hasFocus(), immediate = true)
        }

        private fun animateFocus(view: View, hasFocus: Boolean, immediate: Boolean = false) {
            val scale = if (hasFocus) 1.12f else 1f
            val elevation = if (hasFocus) 24f else 0f
            val duration = if (immediate) 0L else 160L

            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(duration)
                .start()
            card.animate()
                .translationZ(elevation)
                .setDuration(duration)
                .start()

            title.alpha = if (hasFocus) 1f else 0.92f
            subtitle.visibility = if (hasFocus) View.VISIBLE else View.INVISIBLE
        }
    }
}
