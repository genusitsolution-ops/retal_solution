package com.genusit.prms.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.platform.Branding
import com.genusit.prms.platform.BrandingStore
import com.genusit.prms.ui.auth.BrandLogo
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The cached branding (name, tagline, logo) — read only; nothing is downloaded from here. */
@HiltViewModel
class BrandHeaderViewModel @Inject constructor(store: BrandingStore) : ViewModel() {
    val branding: StateFlow<Branding> = store.branding
}

/** Logo, name and tagline with the gold and teal sweep — top of the More menus (1.14). */
@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    val brand by hiltViewModel<BrandHeaderViewModel>().branding.collectAsStateWithLifecycle()
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Card)) {
        BrandWave(Modifier.matchParentSize(), alpha = .45f)
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(brand.logo ?: brand.loginLogo, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(brand.name ?: stringResource(R.string.app_name), color = Palette.Brand, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (brand.showTagline) Text(brand.tagline ?: stringResource(R.string.login_tagline), color = Palette.Teal,
                    style = MaterialTheme.typography.labelMedium, maxLines = 2)
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
