package app.ransel.feature.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ransel.content.stringResource
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.preview.IrisPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.member_add_body
import ransel.composeapp.generated.resources.member_add_confirm
import ransel.composeapp.generated.resources.member_add_error_name
import ransel.composeapp.generated.resources.member_add_error_name_long
import ransel.composeapp.generated.resources.member_add_error_phone
import ransel.composeapp.generated.resources.member_add_name_label
import ransel.composeapp.generated.resources.member_add_name_placeholder
import ransel.composeapp.generated.resources.member_add_phone_hint
import ransel.composeapp.generated.resources.member_add_phone_label
import ransel.composeapp.generated.resources.member_add_phone_suffix
import ransel.composeapp.generated.resources.member_add_title

/** Adds a person by name, with an optional WhatsApp number. The person needs no account. */
@Composable
fun AddMemberSheet(
    form: AddMemberForm,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveSheet(onDismiss = onDismiss) {
        AddMemberContent(form, onName, onPhone, onSubmit)
    }
}

@Composable
private fun AddMemberContent(
    form: AddMemberForm,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.member_add_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.member_add_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IrisTextField(
            label = stringResource(Res.string.member_add_name_label),
            value = form.name,
            onValueChange = onName,
            placeholder = stringResource(Res.string.member_add_name_placeholder),
            error = when (form.nameProblem) {
                NameProblem.REQUIRED -> stringResource(Res.string.member_add_error_name)
                NameProblem.TOO_LONG -> stringResource(Res.string.member_add_error_name_long)
                null -> null
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IrisTextField(
                label = stringResource(Res.string.member_add_phone_label),
                labelSuffix = stringResource(Res.string.member_add_phone_suffix),
                value = form.phone,
                onValueChange = onPhone,
                prefix = null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                error = if (form.phoneInvalid) stringResource(Res.string.member_add_error_phone) else null,
            )
            if (!form.phoneInvalid) {
                Text(
                    stringResource(Res.string.member_add_phone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        LoadingButton(onClick = onSubmit, loading = form.saving, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(Res.string.member_add_confirm), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun AddMemberPreview() = IrisPreview {
    AddMemberContent(AddMemberForm(name = "Rina", phone = "0812-3456-7890"), {}, {}, {})
}

@Preview
@Composable
private fun AddMemberErrorPreview() = IrisPreview {
    AddMemberContent(AddMemberForm(name = "", phone = "123", nameProblem = NameProblem.REQUIRED, phoneInvalid = true), {}, {}, {})
}
