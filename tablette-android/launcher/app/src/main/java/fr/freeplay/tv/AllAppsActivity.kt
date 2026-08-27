package fr.freeplay.tv

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.Collator
import java.util.Locale

/** La liste complète des applications installées, navigable à la manette. */
class AllAppsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_apps)

        val apps = loadApps()
        val grid = findViewById<RecyclerView>(R.id.app_grid)
        grid.layoutManager = GridLayoutManager(this, if (resources.configuration.screenWidthDp >= 900) 6 else 4)
        grid.adapter = AppAdapter(apps)
        grid.setHasFixedSize(true)

        findViewById<TextView>(R.id.app_count).text =
            resources.getQuantityString(R.plurals.app_count, apps.size, apps.size)
    }

    private fun loadApps(): List<ResolveInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.FRANCE)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedWith { a, b ->
                collator.compare(
                    a.loadLabel(pm).toString(),
                    b.loadLabel(pm).toString()
                )
            }
    }

    private inner class AppAdapter(
        private val apps: List<ResolveInfo>
    ) : RecyclerView.Adapter<AppAdapter.AppHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppHolder =
            AppHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app, parent, false)
            )

        override fun getItemCount(): Int = apps.size

        override fun onBindViewHolder(holder: AppHolder, position: Int) =
            holder.bind(apps[position])

        inner class AppHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.app_icon)
            private val label: TextView = view.findViewById(R.id.app_label)

            fun bind(info: ResolveInfo) {
                val pm = packageManager
                label.text = info.loadLabel(pm)
                icon.setImageDrawable(info.loadIcon(pm))

                itemView.setOnClickListener {
                    val pkg = info.activityInfo.packageName
                    Tiles.launchIntentFor(this@AllAppsActivity, pkg)?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        Tiles.safeStart(this@AllAppsActivity, intent)
                    }
                }
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.15f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
                }
            }
        }
    }
}
