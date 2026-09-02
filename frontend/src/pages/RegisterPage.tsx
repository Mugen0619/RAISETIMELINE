import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Alert, Button, Stack, TextField, Typography } from '@mui/material'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import { AuthCard } from '../components/AuthCard'
import { ApiError } from '../api/client'
import { register } from '../api/auth'

const USERNAME_PATTERN = /^[a-zA-Z0-9_]{3,32}$/
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/

interface FormValues {
  email: string
  username: string
  displayName: string
  password: string
  passwordConfirm: string
}

type FieldErrors = Partial<Record<keyof FormValues, string>>

const INITIAL_VALUES: FormValues = {
  email: '',
  username: '',
  displayName: '',
  password: '',
  passwordConfirm: '',
}

function validate(values: FormValues): FieldErrors {
  const errors: FieldErrors = {}

  if (!values.email) {
    errors.email = 'メールアドレスを入力してください。'
  }

  if (!USERNAME_PATTERN.test(values.username)) {
    errors.username = 'ユーザー名は英数字・アンダースコアで3〜32文字にしてください。'
  }

  if (!PASSWORD_PATTERN.test(values.password)) {
    errors.password = 'パスワードは8文字以上で、英字と数字を両方含めてください。'
  }

  if (values.passwordConfirm !== values.password) {
    errors.passwordConfirm = 'パスワードが一致しません。'
  }

  return errors
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [values, setValues] = useState<FormValues>(INITIAL_VALUES)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleChange = (field: keyof FormValues) => (event: ChangeEvent<HTMLInputElement>) => {
    setValues((prev) => ({ ...prev, [field]: event.target.value }))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitError(null)

    const errors = validate(values)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setIsSubmitting(true)
    try {
      await register(values.email, values.password, values.username, values.displayName)
      navigate('/login', { state: { registered: true } })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setSubmitError(
          error.message.includes('username')
            ? 'このユーザー名は既に使用されています。'
            : 'このメールアドレスは既に登録されています。',
        )
      } else if (error instanceof ApiError && error.status === 400) {
        setSubmitError('入力内容を確認してください。')
      } else {
        setSubmitError('登録に失敗しました。時間をおいて再度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthCard title="アカウントを作成" subtitle="RaiseTechタイムラインへようこそ">
      <Stack component="form" spacing={2} onSubmit={handleSubmit} noValidate>
        {submitError && <Alert severity="error">{submitError}</Alert>}

        <TextField
          label="表示名"
          value={values.displayName}
          onChange={handleChange('displayName')}
          placeholder="例）高橋 陽菜"
        />

        <TextField
          label="ユーザー名"
          value={values.username}
          onChange={handleChange('username')}
          placeholder="例）hina_takahashi"
          required
          error={Boolean(fieldErrors.username)}
          helperText={fieldErrors.username}
        />

        <TextField
          label="メールアドレス"
          type="email"
          value={values.email}
          onChange={handleChange('email')}
          placeholder="you@example.com"
          required
          error={Boolean(fieldErrors.email)}
          helperText={fieldErrors.email}
        />

        <TextField
          label="パスワード"
          type="password"
          value={values.password}
          onChange={handleChange('password')}
          placeholder="8文字以上・英数字混合"
          required
          error={Boolean(fieldErrors.password)}
          helperText={fieldErrors.password}
        />

        <TextField
          label="パスワード確認"
          type="password"
          value={values.passwordConfirm}
          onChange={handleChange('passwordConfirm')}
          required
          error={Boolean(fieldErrors.passwordConfirm)}
          helperText={fieldErrors.passwordConfirm}
        />

        <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
          登録してはじめる
        </Button>

        <Typography variant="body2" color="text.secondary" textAlign="center">
          既にアカウントをお持ちですか？{' '}
          <Typography component={RouterLink} to="/login" variant="body2" color="primary" fontWeight={700} sx={{ textDecoration: 'none' }}>
            ログイン
          </Typography>
        </Typography>
      </Stack>
    </AuthCard>
  )
}
